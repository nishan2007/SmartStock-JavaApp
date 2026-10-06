package services;

import data.DB;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Retries private recovery uploads and removes expired local and cloud media. */
final class CustomOrderMediaMaintenance implements AutoCloseable {
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"custom-order-media-maintenance");t.setDaemon(true);return t;});
    void start(){worker.scheduleWithFixedDelay(this::runSafely,1,60,TimeUnit.MINUTES);}
    private void runSafely(){try{if(ServerRoleGuard.state()==ServerRoleGuard.State.PRIMARY)runOnce();}catch(Exception e){System.err.println("Custom order media maintenance failed: "+e.getMessage());}}
    static void runOnce()throws Exception{
        try(Connection c=DB.getConnection()){
            List<UUID> expired=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("DELETE FROM custom_order_file_uploads WHERE expires_at<now() RETURNING upload_id")){try(ResultSet r=p.executeQuery()){while(r.next())expired.add(r.getObject(1,UUID.class));}}
            for(UUID id:expired)try{Files.deleteIfExists(CustomOrderMediaService.stagingPath(id));}catch(Exception e){System.err.println("Expired custom order upload cleanup failed: "+e.getMessage());}
            sweepOrphans(c);
            for(String table:List.of("custom_order_files","custom_order_design_proofs")){
                String freshness="custom_order_files".equals(table)
                    ?" AND removed_at IS NULL AND NOT EXISTS(SELECT 1 FROM custom_orders o WHERE o.custom_order_id=custom_order_files.custom_order_id AND o.status='DELIVERED' AND o.delivered_at<=now()-interval '30 days')"
                    :" AND (decided_at IS NULL OR (status='APPROVED' AND decided_at>now()-interval '3 months') OR (status<>'APPROVED' AND decided_at>now()-interval '30 days'))";
                String sql="SELECT storage_key,content_type FROM "+table+" WHERE cloud_status IN ('PENDING','ERROR') AND deleted_at IS NULL"+freshness+" ORDER BY created_at LIMIT 20";
                try(PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){while(r.next()){String key=r.getString(1),mime=r.getString(2);try{CustomOrderMediaService.mirror(key,mime);setCloud(c,table,key,"PRESENT",null);}catch(Exception e){setCloud(c,table,key,"ERROR",e.getMessage());}}}
            }
            cleanup(c,"custom_order_files","SELECT f.storage_key,f.cloud_status FROM custom_order_files f JOIN custom_orders o ON o.custom_order_id=f.custom_order_id WHERE f.deleted_at IS NULL AND (f.removed_at IS NOT NULL OR (o.status='DELIVERED' AND o.delivered_at<=now()-interval '30 days')) ORDER BY f.created_at LIMIT 50");
            cleanup(c,"custom_order_design_proofs","SELECT storage_key,cloud_status FROM custom_order_design_proofs WHERE deleted_at IS NULL AND ((status IN ('CHANGES_REQUESTED','SUPERSEDED') AND decided_at<=now()-interval '30 days') OR (status='APPROVED' AND decided_at<=now()-interval '3 months')) ORDER BY created_at LIMIT 50");
        }
    }
    private static void sweepOrphans(Connection c)throws Exception{
        Instant cutoff=Instant.now().minusSeconds(25*60*60);
        Path stageDir=CustomOrderMediaService.stagingPath(UUID.randomUUID()).getParent();
        Path assetDir=CustomOrderMediaService.assetPath(UUID.randomUUID().toString()).getParent();
        for(Path directory:List.of(stageDir,assetDir))try(var files=Files.list(directory)){
            for(Path path:files.toList())try{
                String name=path.getFileName().toString();
                if(!Files.isRegularFile(path)||!Files.getLastModifiedTime(path).toInstant().isBefore(cutoff))continue;
                String key=directory.equals(stageDir)&&name.endsWith(".part")?name.substring(0,name.length()-5):name;
                UUID id=UUID.fromString(key);
                String sql=directory.equals(stageDir)?"SELECT 1 FROM custom_order_file_uploads WHERE upload_id=?":"SELECT 1 FROM custom_order_files WHERE file_id=? UNION SELECT 1 FROM custom_order_design_proofs WHERE proof_id=?";
                try(PreparedStatement p=c.prepareStatement(sql)){
                    p.setObject(1,id);if(!directory.equals(stageDir))p.setObject(2,id);
                    try(ResultSet r=p.executeQuery()){if(!r.next())Files.deleteIfExists(path);}
                }
            }catch(Exception e){System.err.println("Custom order orphan cleanup failed: "+e.getMessage());}
        }
    }
    private static void setCloud(Connection c,String table,String key,String status,String error)throws Exception{try(PreparedStatement p=c.prepareStatement("UPDATE "+table+" SET cloud_status=?,cloud_error=? WHERE storage_key=? AND deleted_at IS NULL")){p.setString(1,status);p.setString(2,error==null?null:error.substring(0,Math.min(500,error.length())));p.setString(3,key);p.executeUpdate();}}
    private static void cleanup(Connection c,String table,String query)throws Exception{
        record Row(String key,String cloud){}
        List<Row> rows=new ArrayList<>();try(PreparedStatement p=c.prepareStatement(query);ResultSet r=p.executeQuery()){while(r.next())rows.add(new Row(r.getString(1),r.getString(2)));}
        for(Row row:rows)try{
            Files.deleteIfExists(CustomOrderMediaService.assetPath(row.key()));
            CustomOrderMediaService.deleteCloud(row.key());
            try(PreparedStatement p=c.prepareStatement("UPDATE "+table+" SET deleted_at=now(),cloud_status='PENDING',cloud_error=NULL WHERE storage_key=? AND deleted_at IS NULL")){p.setString(1,row.key());p.executeUpdate();}
        }catch(Exception e){System.err.println("Custom order file retention cleanup failed: "+e.getMessage());}
    }
    @Override public void close(){worker.shutdownNow();}
}
