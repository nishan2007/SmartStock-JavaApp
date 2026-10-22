package services;

import java.sql.*;
import java.util.*;

/** Receipt annotation must remain printable during a loyalty authority outage. */
public final class LoyaltyReceipt {
    private LoyaltyReceipt() { }
    public static String summary(Connection c,String kind,long id)throws SQLException {
        UUID doc;long used,earned;java.math.BigDecimal discount;int customer,location;boolean queued;
        String table="SALE".equals(kind)?"sales":"custom_orders",column="SALE".equals(kind)?"sale_id":"custom_order_id";
        try(var p=c.prepareStatement("SELECT d.document_uuid,d.requested_points,d.discount_amount,t.customer_id,d.source_location_id,d.revision>d.delivered_revision,COALESCE((SELECT sum(earned_points) FROM loyalty_document_lines l WHERE l.document_uuid=d.document_uuid),0) FROM loyalty_documents d JOIN "+table+" t ON t."+column+"=d.local_id WHERE d.document_type=? AND d.local_id=?")){p.setString(1,kind);p.setLong(2,id);try(var r=p.executeQuery()){if(!r.next())return "";doc=(UUID)r.getObject(1);used=r.getLong(2);discount=r.getBigDecimal(3);customer=r.getInt(4);location=r.getInt(5);queued=r.getBoolean(6);earned=r.getLong(7);}}
        StringBuilder text=new StringBuilder();if(used>0)text.append("Points used: ").append(used).append(" (G$").append(discount.toPlainString()).append(" off)\n");
        // A document may still be awaiting payment, production completion or durable delivery.
        boolean released=false;
        try(var p=c.prepareStatement("SELECT earned_points,pending_points FROM loyalty_applied_documents WHERE document_uuid=?")){p.setObject(1,doc);try(var r=p.executeQuery()){if(r.next()&&!queued){earned=r.getLong(1)+r.getLong(2);released=r.getLong(2)==0;}}}
        text.append(released?"Points earned: ":"Points pending: ").append(earned);
        try(var separate=data.DB.getConnection()) {separate.setAutoCommit(false);try{var balance=LoyaltyService.balance(separate,customer,location);text.append("\nAvailable: ").append(balance.get("points").getAsLong()).append(" points = G$").append(balance.get("value").getAsBigDecimal().toPlainString());separate.commit();}catch(Exception e){separate.rollback();text.append("\nBalance temporarily unavailable");}}
        return text.toString();
    }
}
