package Receipt;

import services.LanApiClient.CustomerOpenBalance;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** A statement of open documents, never a proof of payment. */
public final class AccountStatementFormatter {
    private AccountStatementFormatter() { }

    public static String format(String company, String customer, String account,
                                List<CustomerOpenBalance> balances, Instant asOf, ZoneId zone) {
        StringBuilder out = new StringBuilder();
        line(out, company);
        line(out, "UNPAID PURCHASES STATEMENT");
        line(out, "Customer: " + customer);
        line(out, "Account: " + account);
        var date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone);
        line(out, "As of: " + date.format(asOf));
        line(out, "Connected store open balances only.");
        line(out, "Other stores may have additional dues.");
        line(out, "----------------------------------------");
        BigDecimal due = BigDecimal.ZERO;
        int count = 0;
        for (var row : balances) {
            if (row.balanceDue() == null || row.balanceDue().signum() <= 0) continue;
            count++;
            line(out, row.documentType().replace('_', ' ') + ": " + row.documentNumber());
            if (row.createdAtEpochMillis() > 0)
                line(out, "Date: " + date.format(Instant.ofEpochMilli(row.createdAtEpochMillis())));
            line(out, "Total: " + utils.CurrencyFormatter.format(row.total()));
            line(out, "Paid:  " + utils.CurrencyFormatter.format(row.paid()));
            line(out, "Due:   " + utils.CurrencyFormatter.format(row.balanceDue()));
            line(out, "----------------------------------------");
            due = due.add(row.balanceDue());
        }
        if (count == 0) line(out, "No unpaid purchases at this store.");
        line(out, "Open entries: " + count);
        line(out, "TOTAL DUE: " + utils.CurrencyFormatter.format(due));
        line(out, "Statement only. No payment recorded.");
        return out.toString();
    }

    private static void line(StringBuilder out, String value) {
        String clean = (value == null ? "" : value).replaceAll("[\\p{Cntrl}]", " ");
        while (clean.length() > 40) {
            out.append(clean, 0, 40).append('\n');
            clean = clean.substring(40);
        }
        out.append(clean).append('\n');
    }
}
