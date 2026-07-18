package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

final class LanAutoScheduleProposalService {
    private LanAutoScheduleProposalService() { }

    static ServerEmployeeAutoScheduleService.AutoScheduleProposal store(
            Connection c, UUID deviceId, int userId, int locationId,
            ServerEmployeeAutoScheduleService.AutoScheduleProposal proposal) throws Exception {
        String json=LanJson.create().toJson(proposal),hash=LanSecurity.sha256(json);
        try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO lan_api_schedule_proposals(
                    proposal_id,device_id,user_id,location_id,proposal_hash,proposal_json
                ) VALUES(?,?,?,?,?,?)
                ON CONFLICT(proposal_id) DO UPDATE SET proposal_hash=EXCLUDED.proposal_hash,
                    proposal_json=EXCLUDED.proposal_json,created_at=CURRENT_TIMESTAMP,
                    expires_at=CURRENT_TIMESTAMP+INTERVAL '30 minutes',consumed_at=NULL
                """)){p.setObject(1,proposal.proposalId());p.setObject(2,deviceId);p.setInt(3,userId);p.setInt(4,locationId);p.setString(5,hash);p.setString(6,json);p.executeUpdate();}
        return proposal;
    }

    static ServerEmployeeAutoScheduleService.AutoScheduleProposal consume(
            Connection c, UUID deviceId, int userId, int locationId, UUID proposalId) throws Exception {
        String json,hash;try(PreparedStatement p=c.prepareStatement("""
                SELECT proposal_json,proposal_hash FROM lan_api_schedule_proposals
                WHERE proposal_id=? AND device_id=? AND user_id=? AND location_id=?
                  AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP FOR UPDATE
                """)){p.setObject(1,proposalId);p.setObject(2,deviceId);p.setInt(3,userId);p.setInt(4,locationId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("This automatic schedule preview expired or was already applied.");json=r.getString(1);hash=r.getString(2);}}
        if(!LanSecurity.constantTimeEquals(hash,LanSecurity.sha256(json)))throw new IllegalStateException("The stored automatic schedule preview failed integrity validation.");
        try(PreparedStatement p=c.prepareStatement("UPDATE lan_api_schedule_proposals SET consumed_at=CURRENT_TIMESTAMP WHERE proposal_id=? AND consumed_at IS NULL")){p.setObject(1,proposalId);if(p.executeUpdate()!=1)throw new IllegalStateException("This automatic schedule preview was already applied.");}
        return LanJson.create().fromJson(json,ServerEmployeeAutoScheduleService.AutoScheduleProposal.class);
    }
}
