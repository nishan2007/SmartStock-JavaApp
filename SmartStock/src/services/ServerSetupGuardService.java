package services;

import data.DB;
import data.DatabaseConfig;
import models.DeviceInfo;
import utils.DeviceUtils;

import java.sql.Connection;
import java.util.List;

/** Prevents Server Setup from starting a second writable primary for a store. */
public final class ServerSetupGuardService {
    private ServerSetupGuardService() { }

    public static Assessment assess(int locationId) throws Exception {
        List<CloudServerRegistryService.ServerRecord> servers=CloudServerRegistryService.list(locationId);
        String installationId=DeviceUtils.collectDeviceInfo().getInstallationId();
        CloudServerRegistryService.ServerRecord current=servers.stream()
                .filter(s->installationId.equals(s.installationId())).findFirst().orElse(null);
        return new Assessment(current,CloudServerRegistryService.primary(servers),servers.size());
    }

    public static List<LanApiClient.DiscoveredServer> discoverStoreServers(String storeCode) throws Exception {
        String expected=storeCode==null?"":storeCode.trim();
        return LanApiClient.discoverServers().stream()
                .filter(server->expected.isBlank()||expected.equalsIgnoreCase(server.storeCode()))
                .toList();
    }

    public static Assessment registerForStore(int locationId, boolean standby) throws Exception {
        Assessment before=assess(locationId);
        if (before.current()!=null) {
            String currentRole=before.current().role();
            if ("FENCED".equals(currentRole)||"RETIRED".equals(currentRole)) {
                throw new IllegalStateException("This installation is "+currentRole.toLowerCase()
                        +" and cannot be registered again. Use a new server installation identity.");
            }
            CloudServerRegistryService.adoptCurrent(before.current());
            if (standby||"PRIMARY".equals(currentRole)||"DRAINING".equals(currentRole)) return before;
        }
        if(!standby&&before.primary()!=null
                && (before.current()==null||!before.primary().serverInstanceId().equals(before.current().serverInstanceId()))) {
            throw new IllegalStateException("Another primary server is already registered for this store.");
        }
        CloudServerRegistryService.ensureStoreLocation(locationId);
        CloudServerRegistryService.registerCurrent(standby
                ? CloudServerRegistryService.ServerRole.STANDBY
                : CloudServerRegistryService.ServerRole.PRIMARY);
        return assess(locationId);
    }

    /**
     * Authorizes service startup without weakening offline operation for a previously verified primary.
     * Unknown installations must consult the registry before LAN or cloud synchronization can start.
     */
    public static boolean authorizeBackgroundService(Connection local) throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        if (config.locationId() == null) return false;
        try { CloudServerRegistryService.reconcilePendingHandoffRollback(config.locationId()); }
        catch(Exception unavailable) {
            if(CloudServerRegistryService.hasPendingHandoffRollback())ServerRoleGuard.update("PRIMARY");
            System.err.println("Pending server handoff rollback will retry: "+unavailable.getMessage());
        }
        ServerRoleGuard.State state = ServerRoleGuard.state();
        if (state == ServerRoleGuard.State.PRIMARY || state == ServerRoleGuard.State.DRAINING) {
            try {
                CloudServerRegistryService.heartbeatCurrent(local, null);
                state = ServerRoleGuard.state();
            } catch (Exception unavailable) {
                // A known primary stays available when the coordination service or internet is down.
                return true;
            }
            return state == ServerRoleGuard.State.PRIMARY || state == ServerRoleGuard.State.DRAINING;
        }
        if (state != ServerRoleGuard.State.UNKNOWN) {
            try { CloudServerRegistryService.heartbeatCurrent(local,null); }
            catch (Exception unavailable) { System.err.println("Standby registry heartbeat unavailable: "+unavailable.getMessage()); }
            return false;
        }

        Assessment assessment = assess(config.locationId());
        if (assessment.current() != null) {
            CloudServerRegistryService.adoptCurrent(assessment.current());
        } else if (assessment.primary() == null) {
            CloudServerRegistryService.registerCurrent(CloudServerRegistryService.ServerRole.PRIMARY);
        } else {
            return false;
        }
        return ServerRoleGuard.state() == ServerRoleGuard.State.PRIMARY
                || ServerRoleGuard.state() == ServerRoleGuard.State.DRAINING;
    }

    /** Restores and activates only a standby that has a READY verified handoff. */
    public static Activation prepareToStart() throws Exception {
        DatabaseConfig config=DatabaseConfig.load();
        if(config.locationId()==null)throw new IllegalStateException("Select a store before starting server services.");
        Assessment assessment=assess(config.locationId());
        if(assessment.current()==null) {
            if(assessment.primary()==null) {
                registerForStore(config.locationId(),false);
                return new Activation(true,false,"This machine is the store's primary server.");
            }
            throw new IllegalStateException("This machine is not registered as a server for the selected store.");
        }
        String role=assessment.current().role();
        if("PRIMARY".equals(role))return new Activation(true,false,"This machine is the store's primary server.");
        if("FENCED".equals(role)||"RETIRED".equals(role))
            throw new IllegalStateException("This server has been "+role.toLowerCase()+" and cannot be started as primary.");
        CloudServerRegistryService.HandoffStatus handoff=CloudServerRegistryService.handoffStatus(
                config.locationId(),assessment.current().serverInstanceId(),null);
        if(!"READY".equals(handoff.status())||!assessment.current().serverInstanceId().equals(handoff.targetServerInstanceId())) {
            if(assessment.primary()!=null&&"OFFLINE".equals(assessment.primary().health())){
                boolean discovered=LanApiClient.discoverServers().stream().anyMatch(server->
                        assessment.primary().certificateFingerprint().equalsIgnoreCase(server.certificateFingerprint()));
                boolean directlyReachable=LanApiClient.isServerReachable(assessment.primary().endpointHost(),
                        assessment.primary().endpointPort(),assessment.primary().certificateFingerprint());
                if(discovered||directlyReachable)throw new IllegalStateException(
                        "The current primary server is reachable on the store network. Use verified handoff instead of emergency recovery.");
                try(Connection local=DB.getConnection()){
                    StoreHydrationService.VerifiedHydrationResult hydration=StoreHydrationService.restoreVerifiedReplacement(local,config.locationId());
                    if(!hydration.attempted())throw new IllegalStateException("Emergency recovery preparation failed: "+hydration.message());
                    if(assessment.primary().materializedRowCount()==null
                            ||assessment.primary().materializedRowCount()!=hydration.activeRowCount())
                        throw new IllegalStateException("RESTORE_MISMATCH: Restored cloud row counts do not match the primary recovery checkpoint.");
                }
                String recovery=CloudServerRegistryService.markRecoveryReady(config.locationId(),assessment.current().serverInstanceId());
                return new Activation(false,true,"Standby restored recovery point "+recovery+". Sign in on this physical standby as an administrator with Server Recovery permission to approve takeover in Device Management.");
            }
            return new Activation(false,true,"Standby is registered. Start a verified handoff from Device Management on the current primary server.");
        }
        try(Connection local=DB.getConnection()) {
            StoreHydrationService.VerifiedHydrationResult hydration=StoreHydrationService.restoreVerifiedReplacement(local,config.locationId());
            if(!hydration.attempted())throw new IllegalStateException("The verified cloud recovery point could not be restored: "+hydration.message());
            if(handoff.recoveryRowCount()==null||handoff.recoveryRowCount()!=hydration.activeRowCount())
                throw new IllegalStateException("RESTORE_MISMATCH: Restored cloud row counts do not match the handoff checkpoint.");
        }
        CloudServerRegistryService.completeHandoff(config.locationId(),assessment.current().serverInstanceId(),handoff.handoffId());
        return new Activation(true,false,"Verified recovery restored and replacement server activated.");
    }

    public record Assessment(CloudServerRegistryService.ServerRecord current,
                             CloudServerRegistryService.ServerRecord primary,int serverCount) { }
    public record Activation(boolean startServices,boolean standby,String message) { }
}
