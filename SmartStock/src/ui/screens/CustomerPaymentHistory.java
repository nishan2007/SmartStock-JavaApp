package ui.screens;

import Receipt.AccountPaymentReceiptBuilder;
import Receipt.AccountPaymentReceiptData;
import services.LanApiClient;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;
import utils.CurrencyFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class CustomerPaymentHistory extends JFrame {
    private final int customerId;
    private final String customerLabel;
    private final NumberFormat currencyFormat=CurrencyFormatter.create();
    private final DateTimeFormatter dateTimeFormatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private DefaultTableModel paymentModel;private JTable paymentTable;private JLabel summaryLabel;

    public CustomerPaymentHistory(int customerId,String customerLabel){
        this.customerId=customerId;this.customerLabel=customerLabel==null?"Customer Account":customerLabel;
        setTitle("Customer Payment History");setSize(1050,620);setDefaultCloseOperation(DISPOSE_ON_CLOSE);setLayout(new BorderLayout(12,12));
        JPanel main=new JPanel(new BorderLayout(12,12));main.setBorder(new EmptyBorder(14,14,14,14));add(main,BorderLayout.CENTER);
        main.add(buildHeaderPanel(),BorderLayout.NORTH);main.add(buildTablePanel(),BorderLayout.CENTER);main.add(buildSummaryPanel(),BorderLayout.SOUTH);
        loadPayments();WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel(){
        JPanel header=new JPanel(new BorderLayout(12,8));JLabel title=new JLabel("Payment History");title.setFont(new Font("SansSerif",Font.BOLD,24));
        JLabel customer=new JLabel(customerLabel);customer.setFont(new Font("SansSerif",Font.PLAIN,13));JPanel titles=new JPanel();titles.setLayout(new BoxLayout(titles,BoxLayout.Y_AXIS));titles.add(title);titles.add(Box.createVerticalStrut(4));titles.add(customer);
        JButton print=new JButton("Print Receipt"),refresh=new JButton("Refresh");JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));actions.add(print);actions.add(refresh);
        print.addActionListener(e->openSelectedPaymentReceipt());refresh.addActionListener(e->loadPayments());header.add(titles,BorderLayout.WEST);header.add(actions,BorderLayout.EAST);return header;
    }

    private JScrollPane buildTablePanel(){
        paymentModel=new DefaultTableModel(new Object[]{"Payment ID","Payment Date","User","Method","Reference","Device","Drawer","Payment Amount","Applied To","Applied","Charge Total","Paid","Status","Charge Date","Transaction ID"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
        paymentTable=new JTable(paymentModel);paymentTable.setRowHeight(26);paymentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);paymentTable.getTableHeader().setReorderingAllowed(false);
        int[] widths={130,160,150,90,160,120,120,120,150,110,110,110,100,160};for(int i=0;i<widths.length;i++)paymentTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        TableColumn hidden=paymentTable.getColumnModel().getColumn(14);paymentTable.getColumnModel().removeColumn(hidden);return new JScrollPane(paymentTable);
    }

    private JPanel buildSummaryPanel(){JPanel p=new JPanel(new BorderLayout());summaryLabel=new JLabel("Payments: 0");summaryLabel.setBorder(new EmptyBorder(4,2,0,2));p.add(summaryLabel,BorderLayout.WEST);return p;}

    private void loadPayments(){
        paymentModel.setRowCount(0);try{
            LanApiClient.CustomerPaymentResult result=LanApiClient.loadCustomerPayments(customerId);
            for(LanApiClient.CustomerPaymentRecord row:result.payments())paymentModel.addRow(new Object[]{
                    row.paymentId(),formatTimestamp(row.paymentDateEpochMillis()),row.userName(),row.paymentMethod(),row.paymentReference(),
                    row.deviceName(),row.cashDrawerName(),currencyFormat.format(zero(row.paymentAmount())),row.target(),
                    row.appliedAmount()==null?"":currencyFormat.format(row.appliedAmount()),currencyFormat.format(zero(row.chargeTotal())),
                    currencyFormat.format(zero(row.chargePaid())),formatStatus(row.paymentStatus()),formatTimestamp(row.chargeDateEpochMillis()),row.transactionId()});
            summaryLabel.setText("Payments: "+result.paymentCount()+"    Rows: "+result.rowCount()+"    Total Paid: "
                    +currencyFormat.format(zero(result.totalPayments()))+"    Applied: "+currencyFormat.format(zero(result.totalApplied())));
        }catch(Exception ex){JOptionPane.showMessageDialog(this,"Failed to load payment history: "+ex.getMessage(),"SmartStock Server Error",JOptionPane.ERROR_MESSAGE);}
    }

    private void openSelectedPaymentReceipt(){
        if(paymentTable==null||paymentTable.getSelectedRow()<0){JOptionPane.showMessageDialog(this,"Select a payment first.");return;}
        int modelRow=paymentTable.convertRowIndexToModel(paymentTable.getSelectedRow());Object value=paymentModel.getValueAt(modelRow,14);
        if(!(value instanceof Number number)){JOptionPane.showMessageDialog(this,"Selected payment is missing its transaction ID.");return;}
        try{AccountPaymentReceiptData receipt=AccountPaymentReceiptBuilder.loadPaymentReceipt(customerId,number.longValue());WindowHelper.showPosWindow(new AccountPaymentReceiptPreview(receipt),this);}
        catch(Exception ex){JOptionPane.showMessageDialog(this,"Failed to load payment receipt: "+ex.getMessage(),"Payment Receipt",JOptionPane.ERROR_MESSAGE);}
    }

    private String formatTimestamp(long epoch){return epoch<=0?"":Instant.ofEpochMilli(epoch).atZone(StoreTimeZoneHelper.getStoreZone()).format(dateTimeFormatter);}
    private String formatStatus(String status){if(status==null||status.isBlank())return "";return switch(status.toUpperCase()){case"PAID"->"Paid";case"UNPAID"->"Unpaid";default->status.substring(0,1).toUpperCase()+status.substring(1).toLowerCase();};}
    private static BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
}
