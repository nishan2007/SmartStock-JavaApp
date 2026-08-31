package architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedCardAndBadgeTemplateArchitectureTest {
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}

    @Test void orderedUpgradePreservesAndSharesExistingTemplates()throws Exception{
        String migration=read("database/migrations/v1_after/20260827160000_share_card_and_badge_templates.sql");
        String contract=read("src/services/SchemaContractService.java");
        assertTrue(migration.contains("ORDER BY updated_at DESC NULLS LAST,location_id LIMIT 1"));
        assertTrue(migration.contains("badge_template_layout_data=canonical.badge_template_layout_data"));
        assertTrue(migration.contains("customer_card_template_layout_data=canonical.customer_card_template_layout_data"));
        assertTrue(migration.contains("company_template_sharing_version=1"));
        assertTrue(contract.contains("20260827160000_share_card_and_badge_templates.sql"));
        assertTrue(contract.contains("ensureSharedCardAndBadgeTemplatesUpgrade(connection)"));
    }

    @Test void readsAndFutureSavesUseCompanyWideTemplates()throws Exception{
        String server=read("src/services/LanApiServer.java");
        String repository=read("src/managers/ServerCompanyCustomizationRepository.java");
        assertTrue(server.contains("ORDER BY updated_at DESC NULLS LAST,location_id LIMIT 1"));
        assertTrue(server.contains("UPDATE company_customization SET customer_card_template_layout_data=?"));
        assertTrue(repository.contains("sharedBadgeTemplateLocationId"));
        assertTrue(repository.contains("UPDATE company_customization target SET"));
    }
}
