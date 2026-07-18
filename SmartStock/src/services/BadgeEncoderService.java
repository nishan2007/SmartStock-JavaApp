package services;

import managers.CompanyCustomizationManager;

import java.util.List;
import java.util.Objects;

public final class BadgeEncoderService {
    private BadgeEncoderService() {
    }

    public static String programNfc(BadgePrintService.EmployeeBadgeData employee,
                                    CompanyCustomizationManager.BadgeTemplateSettings settings) throws Exception {
        if (!settings.nfcEnabled()) {
            throw new IllegalStateException("RFID/NFC programming is disabled in Company Preferences.");
        }
        String commandTemplate = Objects.requireNonNullElse(settings.nfcWriterCommand(), "").trim();
        if (commandTemplate.isBlank()) {
            throw new IllegalStateException("Set an RFID/NFC writer command in Company Preferences first.");
        }
        String payload = BadgePrintService.buildTrackData(settings.nfcPayloadTemplate(), employee, settings);
        return runCommand(commandTemplate, employee, settings, payload, "RFID/NFC writer");
    }

    public static String verifyNfc(BadgePrintService.EmployeeBadgeData employee,
                                   CompanyCustomizationManager.BadgeTemplateSettings settings) throws Exception {
        String commandTemplate = Objects.requireNonNullElse(settings.nfcVerifyCommand(), "").trim();
        if (commandTemplate.isBlank()) {
            return "RFID/NFC programming completed. No verification command is configured.";
        }
        String payload = BadgePrintService.buildTrackData(settings.nfcPayloadTemplate(), employee, settings);
        return runCommand(commandTemplate, employee, settings, payload, "RFID/NFC verification");
    }

    private static String runCommand(String template,
                                     BadgePrintService.EmployeeBadgeData employee,
                                     CompanyCustomizationManager.BadgeTemplateSettings settings,
                                     String payload,
                                     String label) throws Exception {
        String command = template
                .replace("{badge_id}", employee.badgeId())
                .replace("{employee_id}", String.valueOf(employee.userId()))
                .replace("{full_name}", employee.displayName())
                .replace("{first_name}", Objects.requireNonNullElse(employee.firstName(), ""))
                .replace("{last_name}", Objects.requireNonNullElse(employee.lastName(), ""))
                .replace("{role}", employee.roleName())
                .replace("{company}", settings.companyName())
                .replace("{payload}", payload);
        List<String> args = BadgePrintService.splitWriterCommand(command);
        if (args.isEmpty()) {
            throw new IllegalStateException(label + " command is empty.");
        }
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(label + " exited with code " + exitCode + ".\n" + output);
        }
        return output.isBlank() ? label + " command completed." : output.trim();
    }
}
