package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyNameGreetingArchitectureTest {
    @Test
    void welcomeAndMainMenuUseCompanyNameWithSmartStockFallback() throws Exception {
        String greeting = Files.readString(Path.of("src/ui/helpers/WelcomeGreetingHelper.java"));
        String welcome = Files.readString(Path.of("src/ui/screens/WelcomeFrame.java"));
        String mainMenu = Files.readString(Path.of("src/ui/screens/MainMenu.java"));
        String server = Files.readString(Path.of("src/services/LanApiServer.java"));

        assertTrue(greeting.contains("return value.isBlank() ? \"SmartStock\" : value"));
        assertTrue(greeting.contains("\"Morning, \" + company + \" Team\""));
        assertTrue(welcome.contains("LanApiClient.checkHealth().companyName()"));
        assertTrue(welcome.contains("WelcomeGreetingHelper.currentGreeting(companyDisplayName)"));
        assertTrue(mainMenu.contains("WelcomeGreetingHelper.currentGreeting(companyName).title()"));
        assertTrue(server.contains("data.put(\"companyName\", healthCompanyName())"));
    }
}
