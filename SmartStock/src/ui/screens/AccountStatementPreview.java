package ui.screens;

import Receipt.AccountStatementFormatter;
import Receipt.AccountStatementLetter;
import Receipt.NativeEscPosTransport;
import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;
import services.LanApiClient.CustomerOpenBalance;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import javax.print.*;
import java.awt.*;
import java.awt.print.PrinterJob;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

final class AccountStatementPreview {
    record Snapshot(String name, String accountNumber, List<CustomerOpenBalance> balances) { }
    private record PreviewData(String receipt, AccountStatementLetter letter, List<java.awt.image.BufferedImage> pages) { }

    static void open(JFrame owner, Snapshot snapshot) {
        JDialog dialog = new JDialog(owner, "Unpaid Purchases Statement", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JTextArea preview = new JTextArea(32, 44);
        preview.setEditable(false);
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JButton print = new JButton("Print Receipt");
        JComboBox<String> format = new JComboBox<>(new String[]{"Receipt (40 columns)", "Letter"});
        format.setSelectedIndex(1);
        CardLayout cards=new CardLayout();
        JPanel content=new JPanel(cards);
        content.add(new JScrollPane(preview),"receipt");
        JPanel letterPages=new JPanel();letterPages.setLayout(new BoxLayout(letterPages,BoxLayout.Y_AXIS));
        letterPages.setBackground(new Color(210,215,220));
        content.add(new JScrollPane(letterPages),"letter");
        final AccountStatementLetter[] letter=new AccountStatementLetter[1];
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(format);
        actions.add(print);
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        print.setEnabled(false);
        format.addActionListener(e->{cards.show(content,format.getSelectedIndex()==1?"letter":"receipt");print.setText(format.getSelectedIndex()==1?"Print Letter Statement":"Print Receipt");});
        cards.show(content,"letter");print.setText("Print Letter Statement");
        UiTaskRunner.submit(dialog, "account-statement.format", () -> {
            String company=CompanyCustomizationManager.loadReceiptSettings().companyName();
            Instant asOf=Instant.now();var zone=StoreTimeZoneHelper.getStoreZone();
            var document=new AccountStatementLetter(company,snapshot.name(),snapshot.accountNumber(),snapshot.balances(),asOf,zone);
            List<java.awt.image.BufferedImage> images=new java.util.ArrayList<>();
            for(int i=0;i<document.pageCount();i++)images.add(document.renderPage(i,1.1));
            return new PreviewData(AccountStatementFormatter.format(company,snapshot.name(),snapshot.accountNumber(),snapshot.balances(),asOf,zone),document,List.copyOf(images));
        }, data -> {
            letter[0]=data.letter();
            preview.setText(data.receipt());
            preview.setCaretPosition(0);
            for(var image:data.pages()){
                JLabel page=new JLabel(new ImageIcon(image));page.setAlignmentX(Component.CENTER_ALIGNMENT);
                page.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));letterPages.add(page);
            }
            letterPages.revalidate();letterPages.repaint();
            print.setEnabled(true);
        }, failure -> JOptionPane.showMessageDialog(dialog, failure.getMessage(), "Statement Error", JOptionPane.ERROR_MESSAGE));
        print.addActionListener(e -> {
            if (format.getSelectedIndex() == 1) {
                PrinterJob job=PrinterJob.getPrinterJob();
                job.setJobName("Account statement - "+snapshot.name());
                job.setPrintable(letter[0],AccountStatementLetter.pageFormat());
                if(job.printDialog()){
                    print.setEnabled(false);
                    UiTaskRunner.submit(dialog,"account-statement.letter-print",()->{job.print();return true;},ignored->{
                        print.setEnabled(true);
                        JOptionPane.showMessageDialog(dialog,"Statement sent to the printer.");
                    },failure->{print.setEnabled(true);JOptionPane.showMessageDialog(dialog,failure.getMessage(),"Print Error",JOptionPane.ERROR_MESSAGE);});
                }
                return;
            }
            print.setEnabled(false);
            String text = preview.getText();
            UiTaskRunner.submit(dialog, "account-statement.print", () -> {
                byte[] bytes = ("\u001b@" + text + "\n\n\n\u001dVB\0").getBytes(StandardCharsets.US_ASCII);
                if (NativeEscPosTransport.sendIfEnabled(bytes) == null) {
                    var printer = HardwareSettingsManager.getDefaultReceiptPrinter();
                    PrintService service = printer == null ? PrintServiceLookup.lookupDefaultPrintService()
                            : HardwareSettingsManager.findPrintService(printer.systemName());
                    if (service == null) throw new PrintException("No receipt printer is available. Configure a receipt printer in Hardware Settings.");
                    service.createPrintJob().print(new SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
                }
                return true;
            }, ignored -> {
                print.setEnabled(true);
                JOptionPane.showMessageDialog(dialog, "Statement sent to the receipt printer.");
            }, failure -> {
                print.setEnabled(true);
                JOptionPane.showMessageDialog(dialog, failure.getMessage(), "Print Error", JOptionPane.ERROR_MESSAGE);
            });
        });
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setSize(Math.min(840,screen.width-60),Math.min(820,screen.height-80));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
