package services;

/** No employee data, token interpolation, scripts, or third-party resources. GET never issues a pass. */
final class WalletEnrollmentPage {
    private WalletEnrollmentPage() { }

    static String html() {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>SmartStock Employee Badge</title></head>
                <body><main><h1>SmartStock Employee Badge</h1>
                <p>Open this page on your iPhone. Only continue if your administrator sent you this link.</p>
                <p>This link expires after ten minutes and can issue one badge.
                Creating a badge replaces any existing Wallet badge for your account.</p>
                <form method="post"><button type="submit">Create my Apple Wallet badge</button></form>
                <p>If the download fails, ask your administrator for a new enrollment link.</p>
                </main></body></html>
                """;
    }
}
