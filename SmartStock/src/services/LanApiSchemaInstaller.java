package services;

import java.sql.Connection;
import java.sql.SQLException;

/** Validates the canonical v1 schema for callers retained under the installer API. */
public final class LanApiSchemaInstaller {
    private LanApiSchemaInstaller() {
    }

    public static void ensureSchema(Connection connection) throws SQLException {
        ensureStudioIntakeTables(connection);
        SchemaContractService.ensureMobileItemWebUpgrade(connection);
        SchemaContractService.ensureOptionalNewItemCostPriceUpgrade(connection);
        SchemaContractService.ensureConfigurableTwentyDollarRoundingUpgrade(connection);
        SchemaContractService.ensureEffectiveDatedPayRatesUpgrade(connection);
        SchemaContractService.ensureMissingPayrollBaselinesUpgrade(connection);
        SchemaContractService.ensureMiscSaleItemsUpgrade(connection);
        SchemaContractService.ensureCustomerTypeReceiptPrintingUpgrade(connection);
        SchemaContractService.ensureCustomerCardTemplatesUpgrade(connection);
        SchemaContractService.ensureSharedCardAndBadgeTemplatesUpgrade(connection);
        SchemaContractService.ensureIndividualCustomerTemplateRename(connection);
        SchemaContractService.ensureSchedulerWebUpgrade(connection);
        SchemaContractService.ensureCustomerPhotoCardExpiryUpgrade(connection);
        SchemaContractService.ensureCustomerTypeAccountClassificationUpgrade(connection);
        SchemaContractService.ensureDisplayDevicesUpgrade(connection);
        SchemaContractService.ensureSharedMobileManagementUpgrade(connection);
        SchemaContractService.ensureMobileHomeScreenLinksUpgrade(connection);
        SchemaContractService.ensureEmployeeWebRememberDeviceUpgrade(connection);
        SchemaContractService.ensureMobileDeviceRememberLoginUpgrade(connection);
        SchemaContractService.ensureMobileDeviceInfoUpgrade(connection);
        SchemaContractService.ensureEmployeePhotoFramingUpgrade(connection);
        SchemaContractService.ensurePayrollWebUpgrade(connection);
        SchemaContractService.ensureStoreKeysUpgrade(connection);
        SchemaContractService.ensureCustomerAccountWebUpgrade(connection);
        SchemaContractService.requireLocalReady(connection);
    }

    static void ensureStudioIntakeTables(Connection connection)throws SQLException {
        try(var check=connection.prepareStatement("""
                SELECT to_regclass('public.studio_badge_print_records') IS NOT NULL
                   AND to_regclass('public.studio_template_submission_links') IS NOT NULL
                   AND to_regclass('public.studio_template_submission_sessions') IS NOT NULL
                   AND to_regclass('public.studio_template_submissions') IS NOT NULL
                """);var rows=check.executeQuery()){
            if(rows.next()&&rows.getBoolean(1))return;
        }
        boolean ownTransaction=connection.getAutoCommit();
        if(ownTransaction)connection.setAutoCommit(false);
        try {
            for(String resource:java.util.List.of("20261019140000_studio_badge_records.sql",
                    "20261019150000_studio_template_submissions.sql","20261019150100_studio_submission_permissions.sql")){
                SqlScriptRunner.runSql(connection,SqlScriptRunner.readResource("database/migrations/v1_after/"+resource));
            }
            if(ownTransaction)connection.commit();
        }catch(Exception ex){
            if(ownTransaction)connection.rollback();
            throw new SQLException("Could not initialize the private Studio intake tables.",ex);
        }finally{
            if(ownTransaction)connection.setAutoCommit(true);
        }
    }
}
