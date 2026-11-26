package ui.screens.customorders;

import com.google.gson.*;
import services.CustomOrderDataService.ItemSearchOption;
import services.LanApiClient;
import ui.design.DeckersPalette;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

/** Preview and explicit inventory/refund choices for one order line at a time. */
final class CustomOrderExchangeDialog extends JDialog {
    private record Line(long id,String name) { public String toString(){return name;} }
    private final long orderId;
    private final JComboBox<Line> original=new JComboBox<>();
    private final JTextField search=new JTextField(30),reference=new JTextField(24);
    private final DefaultListModel<ItemSearchOption> matches=new DefaultListModel<>();
    private final JList<ItemSearchOption> replacements=new JList<>(matches);
    private final JComboBox<String> restock=new JComboBox<>(new String[]{"Choose…","Yes — return original to inventory","No — keep original out of inventory"});
    private final JComboBox<String> refundMethod=new JComboBox<>(new String[]{"Choose…","Cash","Card","Cheque","MMG"});
    private final JTextArea reason=new JTextArea(3,30),summary=new JTextArea(7,34);
    private final JButton find=new JButton("Search"),previewButton=new JButton("Preview exchange"),save=new JButton("Exchange item");
    private final Consumer<Boolean> completion;
    private JsonObject preview;
    private JsonObject pendingRequest;
    private long generation;
    private String key=UUID.randomUUID().toString();
    private boolean busy;

    static void show(Component parent,long orderId,JsonObject details,Consumer<Boolean> completion) {
        new CustomOrderExchangeDialog(parent,orderId,details,completion).setVisible(true);
    }
    private CustomOrderExchangeDialog(Component parent,long orderId,JsonObject details,Consumer<Boolean> completion) {
        super(SwingUtilities.getWindowAncestor(parent),"Exchange order item",ModalityType.APPLICATION_MODAL);
        this.orderId=orderId;this.completion=completion;
        for(var value:details.getAsJsonArray("lines")) {
            var line=value.getAsJsonObject();
            original.addItem(new Line(line.get("custom_order_line_id").getAsLong(),"#"+line.get("custom_order_line_id").getAsLong()+" · "+CustomOrderDetailsPanel.text(line,"item_name")+" / "+CustomOrderDetailsPanel.text(line,"variant_name")+" · "+CustomOrderDetailsPanel.text(line,"item_size")+" "+CustomOrderDetailsPanel.text(line,"item_color")+" — $"+CustomOrderDetailsPanel.text(line,"line_total")));
        }
        JPanel body=new JPanel(new GridBagLayout());body.setBackground(DeckersPalette.surface());body.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
        int row=0;addRow(body,row++,"Original item",original);
        JPanel searchPanel=new JPanel(new BorderLayout(6,0));searchPanel.add(search);searchPanel.add(find,BorderLayout.EAST);addRow(body,row++,"Replacement",searchPanel);
        replacements.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);replacements.setVisibleRowCount(4);addRow(body,row++,"Search results",new JScrollPane(replacements));
        addRow(body,row++,"Return original to stock?",restock);
        reason.setLineWrap(true);reason.setWrapStyleWord(true);addRow(body,row++,"Reason",new JScrollPane(reason));
        summary.setEditable(false);summary.setLineWrap(true);summary.setWrapStyleWord(true);summary.setText("Search for an inventory item, select its variant, and preview the price difference.\nPrinting charges and the existing line discount are retained. Production for the replacement restarts at Not started.");
        addRow(body,row++,"Price preview",new JScrollPane(summary));
        addRow(body,row++,"Refund method (if due)",refundMethod);addRow(body,row++,"Refund reference",reference);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));JButton cancel=new JButton("Cancel");buttons.add(cancel);buttons.add(previewButton);buttons.add(save);
        for(JComponent field:new JComponent[]{original,search,replacements,restock,reason,summary,refundMethod,reference})OrdersDesign.field(field);
        for(JButton button:new JButton[]{cancel,previewButton,save,find})OrdersDesign.style(button,button==save);
        searchPanel.setOpaque(false);buttons.setOpaque(false);
        GridBagConstraints gbc=new GridBagConstraints();gbc.gridx=0;gbc.gridy=row;gbc.gridwidth=2;gbc.fill=GridBagConstraints.HORIZONTAL;body.add(buttons,gbc);
        JPanel topAligned=new JPanel(new BorderLayout());topAligned.setBackground(DeckersPalette.surface());topAligned.add(body,BorderLayout.NORTH);
        setContentPane(new JScrollPane(topAligned));save.setEnabled(false);
        refundMethod.setEnabled(false);reference.setEnabled(false);
        find.addActionListener(e->search());search.addActionListener(e->search());
        original.addActionListener(e->invalidatePreview());replacements.addListSelectionListener(e->{if(!e.getValueIsAdjusting())invalidatePreview();});
        previewButton.addActionListener(e->preview());save.addActionListener(e->save());cancel.addActionListener(e->{if(!busy)dispose();});
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Rectangle screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(560,540));setSize(Math.min(760,screen.width-32),Math.min(760,screen.height-32));setLocationRelativeTo(parent);
    }
    private static void addRow(JPanel panel,int row,String label,Component field) {
        GridBagConstraints c=new GridBagConstraints();c.gridy=row;c.gridx=0;c.anchor=GridBagConstraints.NORTHWEST;c.insets=new Insets(5,3,5,9);panel.add(OrdersDesign.label(label,16,false),c);
        c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;panel.add(field,c);
    }
    private void invalidatePreview(){generation++;preview=null;save.setEnabled(false);refundMethod.setEnabled(false);reference.setEnabled(false);key=UUID.randomUUID().toString();}
    private void search() {
        if(busy)return;
        invalidatePreview();matches.clear();String query=search.getText().trim();if(query.isBlank())return;
        find.setEnabled(false);long token=generation;
        UiTaskRunner.submit(this,"exchange.search",()->LanApiClient.searchCustomOrderItems(query),result->{find.setEnabled(true);if(token!=generation)return;for(var item:result)matches.addElement(item);if(matches.isEmpty())summary.setText("No matching inventory items. Try an item name, SKU or barcode.");},e->{find.setEnabled(true);error(e);});
    }
    private JsonObject request() {
        Line line=(Line)original.getSelectedItem();ItemSearchOption item=replacements.getSelectedValue();
        if(line==null||item==null)throw new IllegalArgumentException("Select the original item and a replacement variant first.");
        JsonObject r=new JsonObject();r.addProperty("orderId",orderId);r.addProperty("lineId",line.id());r.addProperty("itemId",item.customItemId());if(item.customVariantId()!=null)r.addProperty("variantId",item.customVariantId());return r;
    }
    private void preview() {
        if(busy)return;
        try {
            JsonObject request=request();invalidatePreview();long token=generation;previewButton.setEnabled(false);
            UiTaskRunner.submit(this,"exchange.preview",()->LanApiClient.previewCustomOrderExchange(request),result->{
                previewButton.setEnabled(true);if(token!=generation)return;preview=result;
                summary.setText("Original item: "+result.get("oldItem").getAsString()+"\nReplacement: "+result.get("newItem").getAsString()+
                        "\nLine price: $"+result.get("oldLineTotal")+" to $"+result.get("newLineTotal")+" (difference $"+result.get("difference")+")"+
                        "\nNew order total: $"+result.get("orderTotal")+"\nRemaining balance: $"+result.get("balance")+"\nRefund required: $"+result.get("refund"));
                boolean refund=result.get("refund").getAsBigDecimal().signum()>0;refundMethod.setEnabled(refund);reference.setEnabled(refund);save.setEnabled(true);
            },e->{previewButton.setEnabled(true);error(e);});
        } catch(Exception e){error(e);}
    }
    private void save() {
        if(busy||preview==null)return;
        if(pendingRequest!=null){submit(pendingRequest);return;}
        try {
            if(restock.getSelectedIndex()==0)throw new IllegalArgumentException("Choose whether to return the original item to inventory.");
            if(reason.getText().isBlank())throw new IllegalArgumentException("Enter the reason for exchanging this item.");
            BigDecimal refund=preview.get("refund").getAsBigDecimal();
            if(refund.signum()>0 && refundMethod.getSelectedIndex()==0)throw new IllegalArgumentException("Choose how to refund $"+refund+" before exchanging.");
            if(refund.signum()>0 && refundMethod.getSelectedIndex()>1 && reference.getText().isBlank())throw new IllegalArgumentException("Enter the refund transaction reference.");
            JsonObject r=request();r.addProperty("restock",restock.getSelectedIndex()==1);r.addProperty("reason",reason.getText().trim());r.add("fingerprint",preview.get("fingerprint"));
            if(refund.signum()>0){r.addProperty("refundMethod",refundMethod.getSelectedItem().toString().toUpperCase(java.util.Locale.ROOT));r.addProperty("refundReference",reference.getText().trim());}
            if(preview.get("requireAuthorization").getAsBoolean()) {
                var authorization=ui.helpers.CustomerChargeAuthorizationDialog.capture(this,preview.get("customerName").getAsString());
                if(authorization==null)return;
                r.add("authorization",new Gson().toJsonTree(authorization));
            }
            String message=summary.getText()+"\n\nOriginal item: "+restock.getSelectedItem()+"\n"+(refund.signum()>0?"Refund $"+refund+" through "+refundMethod.getSelectedItem()+" now. External refunds must be processed through the payment provider before confirming.\n":"")+"Save this exchange and restart production for the replacement?";
            if(JOptionPane.showConfirmDialog(this,message,"Confirm item exchange",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
            pendingRequest=r.deepCopy();submit(pendingRequest);
        } catch(Exception e){error(e);}
    }
    private void submit(JsonObject request) {
        busy=true;save.setEnabled(false);previewButton.setEnabled(false);find.setEnabled(false);original.setEnabled(false);replacements.setEnabled(false);restock.setEnabled(false);reason.setEnabled(false);search.setEnabled(false);refundMethod.setEnabled(false);reference.setEnabled(false);setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        UiTaskRunner.submit(this,"exchange.save",()->LanApiClient.saveCustomOrderExchange(request,key),result->{completion.accept(true);dispose();},e->{
            busy=false;setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            if(e instanceof LanApiClient.LanApiException api && java.util.Set.of("EXCHANGE_REJECTED","ADMIN_REQUIRED","VALIDATION_ERROR","STORE_REGISTER_REQUIRED").contains(api.code())) {
                pendingRequest=null;invalidatePreview();save.setText("Exchange item");previewButton.setEnabled(true);find.setEnabled(true);original.setEnabled(true);replacements.setEnabled(true);restock.setEnabled(true);reason.setEnabled(true);search.setEnabled(true);
            } else {
                // Keep the exact request and key frozen: the server may have committed before the connection failed.
                save.setText("Retry exchange");save.setEnabled(true);
            }
            error(e);
        });
    }
    private void error(Throwable error){Throwable e=error;while(e.getCause()!=null)e=e.getCause();JOptionPane.showMessageDialog(this,e.getMessage(),"Item exchange",JOptionPane.ERROR_MESSAGE);}
}
