package services;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Ephemeral progress only. Requests retain the existing image response and authorization. */
public final class StudioJobs {
    private static final Map<String,Job> jobs=new ConcurrentHashMap<>();
    public static synchronized Job begin(String owner,String id){
        if(id==null||!id.matches("[a-fA-F0-9-]{36}")||!UUID.fromString(id).toString().equalsIgnoreCase(id))throw new IllegalArgumentException("Invalid image request identifier");
        long now=System.currentTimeMillis();jobs.values().removeIf(j->j.finished>0&&now-j.finished>600000);
        if(jobs.size()>=32)throw new IllegalArgumentException("Too many image requests; try again shortly");
        Job job=new Job(owner);
        if(jobs.putIfAbsent(id,job)!=null)throw new IllegalArgumentException("Image request identifier already used");return job;
    }
    public static Map<String,Object> status(String owner,String id,boolean cancel){
        Job job=jobs.get(id);if(job==null||!job.owner.equals(owner))return Map.of("state","unknown");
        synchronized(job){if(cancel&&job.finished==0)job.cancel=true;return job.snapshot();}
    }
    public static final class Job implements StudioImageEnhancer.Progress,AutoCloseable {
        private final String owner;
        private String device="",note="Preparing image",state="running";
        private int completed,total;
        private long started=System.currentTimeMillis(),finished;
        private volatile boolean cancel;
        private Job(String owner){this.owner=owner;}
        public synchronized void update(String device,int completed,int total,String note){
            if(!this.device.equals(device)||this.total!=total)started=System.currentTimeMillis();
            this.device=device;this.completed=completed;this.total=total;this.note=note;
        }
        public boolean cancelled(){return cancel;}
        public synchronized void success(){state="complete";}
        private Map<String,Object> snapshot(){
            long elapsed=System.currentTimeMillis()-started;
            return Map.of("state",state,"device",device,"completed",completed,"total",total,"note",note,
                "cancelRequested",cancel,"percent",total==0?0:completed*100/total,
                "remainingSeconds",completed>=2&&total>completed?elapsed/1000*(total-completed)/completed:-1);
        }
        public synchronized void close(){if(state.equals("running"))state=cancel?"cancelled":"failed";finished=System.currentTimeMillis();}
    }
}
