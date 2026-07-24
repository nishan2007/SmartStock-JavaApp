package services;

import java.time.Duration;

/** Small operational tool for verifying the configured PC/SC reader without opening the UI. */
public final class NfcCardTool {
    private NfcCardTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "write".equalsIgnoreCase(args[0])) {
            System.out.println(PcscNfcService.writeAndVerify(args[1]));
            return;
        }
        if (args.length == 1 && "read".equalsIgnoreCase(args[0])) {
            PcscNfcService.ReadResult result = PcscNfcService.read(Duration.ofSeconds(20));
            System.out.println("Card UID: " + result.cardUid());
            System.out.println("Payload: " + result.payload());
            return;
        }
        throw new IllegalArgumentException("Usage: NfcCardTool write <badge-id> | read");
    }
}
