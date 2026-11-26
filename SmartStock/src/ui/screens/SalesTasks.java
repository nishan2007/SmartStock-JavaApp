package ui.screens;

import com.google.gson.*;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.design.DeckersPalette;
import ui.helpers.CustomerChargeAuthorizationDialog;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Desktop work queue; all persistence and financial rules live on the LAN server. */
public final class SalesTasks extends JFrame {
    private SalesTaskWorkspace workspace;
    private String lastAction="SAVE";
    private Long loadingTaskId;
    private final JLabel message=new JLabel("Loading tasks…"){@Override public void setText(String value){super.setText(value);setVisible(value!=null&&(value.startsWith("Loading")||value.startsWith("Saving")));}};
    private JsonObject options=new JsonObject(),selected;
    private boolean busy;
    private Long pendingSelection;
    private ZoneId zone=ZoneId.systemDefault();
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public SalesTasks() {this(null);}
    public SalesTasks(Long taskId) {
        super("Sales Tasks");setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(1180,760);setMinimumSize(new Dimension(850,580));setLocationRelativeTo(null);
        if(taskId!=null)pendingSelection=taskId;
        setJMenuBar(AppMenuBar.create(this,"SalesTasks"));
        setSize(1440,1024);setMinimumSize(new Dimension(980,700));
        workspace=new SalesTaskWorkspace(new SalesTaskWorkspace.Handler(){
            public void select(long id){loadTask(id);}
            public void refresh(String query){SalesTasks.this.refresh();}
            public void save(String action){saveWorkspace(action);}
            public void action(String action){try{workspaceAction(action);}catch(Exception e){error(e);}}
            public boolean discardChanges(){return JOptionPane.showConfirmDialog(SalesTasks.this,"Discard the unsaved changes to this task?","Unsaved task",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
        });
        if(taskId!=null){workspace.search.setText("ST-"+taskId);workspace.filter.setSelectedItem("All tasks");}
        JPanel root=new JPanel(new BorderLayout());root.setBackground(DeckersPalette.background());root.add(buildBrandHeader(),BorderLayout.NORTH);root.add(workspace,BorderLayout.CENTER);message.setBorder(BorderFactory.createEmptyBorder(5,14,5,14));root.add(message,BorderLayout.SOUTH);setContentPane(root);
        addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosing(java.awt.event.WindowEvent e){dispose();}});setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        UiTaskRunner.submit(this,"task-options",()->read("OPTIONS"),o->{options=o;if(o.has("timezone"))zone=ZoneId.of(o.get("timezone").getAsString());workspace.setOptions(o,zone);ui.helpers.ThemeManager.applyToWindow(this);refresh();},this::error);
        ui.helpers.ThemeManager.applyToWindow(this);
        ui.helpers.WindowHelper.configurePosWindow(this);
    }
    private JPanel buildBrandHeader(){
        JPanel header=createBrandHeader("SmartStock",null,java.util.Objects.toString(managers.SessionManager.getCurrentLocationName(),"—"),java.util.Objects.toString(managers.SessionManager.getCurrentUserDisplayName(),"—"),java.time.LocalDate.now(ui.helpers.StoreTimeZoneHelper.getStoreZone()));
        UiTaskRunner.submit(this,"task-branding",()->{var settings=managers.CompanyCustomizationManager.loadReceiptSettings();var image=managers.CompanyCustomizationManager.loadCompanyLogo(settings);return new TaskBranding(settings.companyName(),image==null?null:new ImageIcon(ui.design.DeckersLogoManager.scaleToFit(image,190,64)));},branding->{JLabel company=(JLabel)header.getClientProperty("company");JLabel logo=(JLabel)header.getClientProperty("logo");company.setText(branding.name());logo.setIcon(branding.logo());logo.setVisible(branding.logo()!=null);},e->{((JLabel)header.getClientProperty("logo")).setVisible(false);});
        return header;
    }
    static JPanel createBrandHeader(String name,Icon icon,String store,String staff,java.time.LocalDate date){
        JPanel header=new JPanel(new BorderLayout(28,8));header.setBackground(DeckersPalette.background());header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,DeckersPalette.border()),BorderFactory.createEmptyBorder(8,28,8,24)));header.setPreferredSize(new Dimension(1000,82));
        JLabel logo=new JLabel(icon);logo.setPreferredSize(new Dimension(190,64));logo.setVisible(icon!=null);JLabel company=new JLabel(name);company.setFont(SalesTaskVisuals.font(Font.BOLD,27));company.setForeground(DeckersPalette.text());
        JPanel titles=new JPanel(new GridLayout(2,1,0,2));titles.setOpaque(false);titles.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,1,0,0,DeckersPalette.border()),BorderFactory.createEmptyBorder(0,28,0,0)));titles.add(company);JLabel subtitle=new JLabel("Sales Tasks");subtitle.setFont(SalesTaskVisuals.font(Font.PLAIN,17));titles.add(subtitle);JPanel brand=new JPanel(new BorderLayout(24,0));brand.setOpaque(false);brand.add(logo,BorderLayout.WEST);brand.add(titles,BorderLayout.CENTER);header.add(brand,BorderLayout.CENTER);
        JPanel meta=new JPanel(new GridLayout(1,3,24,0));meta.setOpaque(false);meta.add(brandMeta("Store",store,"pin"));meta.add(brandMeta("Staff",staff,"person"));meta.add(brandMeta(date.format(DateTimeFormatter.ofPattern("EEEE")),date.format(DateTimeFormatter.ofPattern("d MMM yyyy")),"calendar"));header.add(meta,BorderLayout.EAST);header.putClientProperty("company",company);header.putClientProperty("logo",logo);return header;
    }
    private static JPanel brandMeta(String title,String value,String symbol){JPanel panel=new JPanel(new BorderLayout(12,0));panel.setOpaque(false);panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,1,0,0,DeckersPalette.border()),BorderFactory.createEmptyBorder(3,20,3,0)));panel.add(new JLabel(SalesTaskVisuals.icon(symbol,DeckersPalette.muted(),22)),BorderLayout.WEST);JLabel text=new JLabel("<html>"+escape(title)+"<br><b>"+escape(value)+"</b></html>");text.setFont(SalesTaskVisuals.font(Font.PLAIN,15));text.setForeground(DeckersPalette.text());panel.add(text);return panel;}
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private record TaskBranding(String name,ImageIcon logo){}
    @Override protected void processWindowEvent(java.awt.event.WindowEvent event){
        if(event.getID()==java.awt.event.WindowEvent.WINDOW_CLOSING
                &&(busy||workspace==null||!workspace.canLeave()))return;
        super.processWindowEvent(event);
    }
    private void loadTask(long id){
        loadingTaskId=id;selected=null;workspace.setBusy(true);JsonObject b=new JsonObject();b.addProperty("action","DETAILS");b.addProperty("taskId",id);
        UiTaskRunner.submit(this,"task-details",()->LanApiClient.salesTasksRead(b),r->{if(!java.util.Objects.equals(loadingTaskId,id))return;selected=r;workspace.showTask(r);ui.helpers.ThemeManager.applyToWindow(this);workspace.showStepAfterSave(lastAction);lastAction="SAVE";workspace.setBusy(false);},e->{workspace.setBusy(false);error(e);});
    }
    private void saveWorkspace(String nextAction){
        if(busy)return;
        try {
            JsonObject draft=workspace.draft();boolean creating=workspace.isNew();
            if("FINALIZE".equals(nextAction)&&!approvePricing(draft.getAsJsonObject("checkout")))return;
            JsonObject request=creating?draft:command("EDIT");if(!creating)merge(request,draft);request.addProperty("action",creating?"CREATE":"EDIT");
            String saveKey=UUID.randomUUID().toString(),transitionKey=UUID.randomUUID().toString();
            saveAndTransition(request,saveKey,nextAction,transitionKey);
        }catch(Exception e){error(e);}
    }
    private void saveAndTransition(JsonObject request,String saveKey,String next,String transitionKey){
        busy=true;workspace.setBusy(true);message.setText("Saving task…");
        UiTaskRunner.submit(this,"task-update",()->{
            JsonObject saved=LanApiClient.salesTasksUpdate(request,saveKey);long id=saved.get("taskId").getAsLong();
            if(!next.equals("SAVE")){
                JsonObject transition=new JsonObject();transition.addProperty("action",next);transition.addProperty("taskId",id);transition.addProperty("version",request.has("version")?request.get("version").getAsInt()+1:1);
                if(next.equals("FINALIZE"))transition.add("checkout",request.get("checkout"));
                LanApiClient.salesTasksUpdate(transition,transitionKey);
            }
            return saved;
        },r->{busy=false;workspace.setBusy(false);lastAction=next;pendingSelection=r.get("taskId").getAsLong();refresh();},e->{busy=false;workspace.setBusy(false);error(e);if(JOptionPane.showConfirmDialog(this,"Retry this saved request using the same retry references?","Retry",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)saveAndTransition(request,saveKey,next,transitionKey);});
    }
    private void workspaceAction(String action){
        if(busy)return;
        if(workspace.isNew()&&!action.equals("ADD_SERVICE"))throw new IllegalArgumentException("Create the task first by saving its customer and work instructions.");
        if(List.of("PAYMENT","REFUND","COLLECT","PRINT").contains(action)&&workspace.isDirty())throw new IllegalArgumentException("Save progress before taking payments, collecting or printing this task.");
        switch(action){
            case "PRINT"->print();case "ATTACH"->{if(!workspace.isNew()){if(workspace.isDirty())throw new IllegalArgumentException("Save progress before attaching files.");attach();}}
            case "DOWNLOAD"->download();case "PAYMENT"->payment(false);case "REFUND"->payment(true);case "COLLECT"->collect();case "RECEIPT"->finalReceipt();
            case "REOPEN","REOPEN_PRICING","CANCEL"->{if(workspace.isDirty())throw new IllegalArgumentException("Save or discard changes first.");change(action,true);}
            case "PRICING"->workspace.goTo(2);
            case "HISTORY"->{JTextArea history=new JTextArea(slip(selected),22,65);history.setEditable(false);history.setLineWrap(true);history.setWrapStyleWord(true);JOptionPane.showMessageDialog(this,new JScrollPane(history),"Task history",JOptionPane.PLAIN_MESSAGE);}
            case "ADD_SERVICE"->{
                Integer id=null;if(options.has("products"))for(JsonElement el:options.getAsJsonArray("products")){JsonObject p=el.getAsJsonObject();if(str(p,"sku").equals("SMARTSTOCK-MISC"))id=p.get("product_id").getAsInt();}
                if(id==null)throw new IllegalArgumentException("The miscellaneous service item is unavailable.");
                JTextField name=new JTextField(),price=new JTextField();JPanel form=new JPanel(new GridLayout(0,2,8,8));field(form,"Description",name);field(form,"Unit price",price);
                if(JOptionPane.showConfirmDialog(this,form,"Service charge",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){if(name.getText().isBlank())throw new IllegalArgumentException("Enter a service description.");workspace.addServiceLine(id,name.getText(),new BigDecimal(price.getText().trim()),1);}
            }
        }
    }
    private JsonObject read(String action)throws Exception{JsonObject b=new JsonObject();b.addProperty("action",action);return LanApiClient.salesTasksRead(b);}
    private void refresh(){
        String query=workspace.search.getText().trim();Long current=pendingSelection!=null?pendingSelection:selected==null?null:selected.getAsJsonObject("task").get("task_id").getAsLong();pendingSelection=null;
        JsonObject b=new JsonObject();b.addProperty("action","LIST");b.addProperty("search",query);
        UiTaskRunner.submit(this,"task-list",()->LanApiClient.salesTasksRead(b),r->{workspace.setJobs(r.getAsJsonArray("tasks"),current);message.setText(r.getAsJsonArray("tasks").size()+" tasks — final prices are confirmed after work is completed.");if(current!=null)loadTask(current);},this::error);
    }
    private JsonObject command(String action){if(selected==null)throw new IllegalArgumentException("Select a task and wait for its details to load.");JsonObject t=selected.getAsJsonObject("task"),b=new JsonObject();b.addProperty("action",action);b.add("taskId",t.get("task_id"));b.add("version",t.get("version"));return b;}
    private void send(JsonObject b){send(b,UUID.randomUUID().toString());}
    private void send(JsonObject b,String key){busy=true;setButtons(false);message.setText("Saving…");UiTaskRunner.submit(this,"task-update",()->LanApiClient.salesTasksUpdate(b,key),r->{busy=false;setButtons(true);pendingSelection=r.get("taskId").getAsLong();message.setText("Saved ST-"+r.get("taskId").getAsString());if(r.has("sale")&&r.getAsJsonObject("sale").has("changeDue")&&!r.getAsJsonObject("sale").get("changeDue").isJsonNull())JOptionPane.showMessageDialog(this,"Change due: "+r.getAsJsonObject("sale").get("changeDue").getAsString());refresh();},e->{busy=false;setButtons(true);error(e);if(JOptionPane.showConfirmDialog(this,"Retry this exact request? A retry uses the same reference to prevent duplicate payments.","Retry",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)send(b,key);});}
    private void setButtons(boolean enabled){workspace.setBusy(!enabled);}
    private void change(String action,boolean reason){JsonObject b=command(action);if(reason){String value=JOptionPane.showInputDialog(this,"Reason for this change:");if(value==null)return;b.addProperty("reason",value);}send(b);}
    private record Option(Integer id,String label,JsonObject source){public String toString(){return label;}}
    private static void field(JPanel p,String label,JComponent input){
        if(p.getLayout() instanceof GridBagLayout){
            GridBagConstraints c=new GridBagConstraints();c.gridy=p.getComponentCount()/2;c.gridx=0;c.insets=new Insets(4,4,4,8);c.anchor=GridBagConstraints.WEST;
            p.add(new JLabel(label),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;p.add(input,c);
        }else{p.add(new JLabel(label));p.add(input);}
    }
    private JsonObject tender(String title,boolean amount,boolean account){
        JsonObject b=new JsonObject();JTextField value=new JTextField(),ref=new JTextField(),reason=new JTextField();
        JComboBox<String> method=new JComboBox<>(account?new String[]{"CASH","CARD","MMG","CHEQUE","BANK_TRANSFER","ACCOUNT"}:new String[]{"CASH","CARD","MMG","CHEQUE","BANK_TRANSFER"});
        JPanel p=new JPanel(new GridLayout(0,2,8,8));if(amount)field(p,"Amount",value);field(p,"Payment method",method);field(p,"Reference (required for non-cash)",ref);if(title.equals("Refund"))field(p,"Refund reason",reason);
        if(JOptionPane.showConfirmDialog(this,p,title,JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;
        if(amount)b.addProperty("amount",new BigDecimal(value.getText().trim()));b.addProperty("method",(String)method.getSelectedItem());b.addProperty("reference",ref.getText());b.addProperty("reason",reason.getText());
        if("ACCOUNT".equals(method.getSelectedItem())){var auth=CustomerChargeAuthorizationDialog.capture(this,str(selected.getAsJsonObject("task"),"customer_name"));if(auth==null)return null;b.add("authorization",new Gson().toJsonTree(auth));}return b;
    }
    private void payment(boolean refund){JsonObject b=command(refund?"REFUND":"PAYMENT"),t=tender(refund?"Refund":"Advance payment",true,false);if(t==null)return;merge(b,t);send(b);}
    private void collect(){
        JsonObject b=command("COLLECT"),task=selected.getAsJsonObject("task");
        if(!"FINALIZED".equals(str(task,"billing_status")))throw new IllegalArgumentException("Finalize the bill before collection.");
        BigDecimal balance=task.get("final_total").getAsBigDecimal().subtract(selected.get("paid").getAsBigDecimal());
        if(balance.signum()<0)throw new IllegalArgumentException("Refund the excess advance before collection.");
        if(balance.signum()==0){b.addProperty("method","CASH");b.addProperty("reference","");send(b);return;}
        JsonObject tender=tender("Collect — outstanding: "+balance,false,true);if(tender==null)return;merge(b,tender);
        if("CASH".equals(tender.get("method").getAsString())&&balance.signum()>0){String cash=JOptionPane.showInputDialog(this,"Cash received (balance "+balance+"):",balance.toPlainString());if(cash==null)return;b.addProperty("cashCollected",new BigDecimal(cash.trim()));}
        send(b);
    }
    private void finalReceipt(){
        command("DETAILS");String id=str(selected.getAsJsonObject("task"),"sale_id");
        if(id.isBlank())throw new IllegalArgumentException("The final receipt is available after collection.");
        UiTaskRunner.submit(this,"task-sale-receipt",()->LanApiClient.loadSaleReceipt(Integer.parseInt(id),null,null),receipt->new ReceiptPreview(receipt,true).setVisible(true),this::error);
    }
    private boolean approvePricing(JsonObject checkout){
        for(JsonElement el:checkout.getAsJsonArray("lines")) {
            JsonObject line=el.getAsJsonObject();
            if(!line.has("unitPrice")||!line.has("quantity"))throw new IllegalArgumentException("Enter all final quantities and prices.");
            boolean misc=line.has("miscItem")&&line.get("miscItem").getAsBoolean();
            if(!misc&&!managers.PermissionManager.hasPermission("CHANGE_SALE_ITEM_PRICE")) {
                for(JsonElement product:options.getAsJsonArray("products")) {
                    JsonObject catalog=product.getAsJsonObject();
                    if(catalog.get("product_id").getAsInt()==line.get("productId").getAsInt()&&catalog.get("price").getAsBigDecimal().compareTo(line.get("unitPrice").getAsBigDecimal())!=0)
                        if(!approval(line,"CHANGE_SALE_ITEM_PRICE","priceApprovalToken","priceOverrideReason"))return false;
                }
            }
            if(line.get("discountPercent").getAsBigDecimal().signum()>0&&!managers.PermissionManager.hasPermission("APPLY_SALE_DISCOUNT"))
                if(!approval(line,"APPLY_SALE_DISCOUNT","discountApprovalToken","discountOverrideReason"))return false;
        }
        BigDecimal percent=checkout.get("saleDiscountPercent").getAsBigDecimal();
        BigDecimal limit=options.has("discountLimit")?options.get("discountLimit").getAsBigDecimal():new BigDecimal("5");
        String permission=percent.compareTo(limit)>0?"SALE_DISCOUNT_OVERRIDE":"APPLY_SALE_DISCOUNT";
        return percent.signum()<=0||managers.PermissionManager.hasPermission(permission)||approval(checkout,permission,"saleDiscountApprovalToken","saleDiscountOverrideReason");
    }
    private boolean approval(JsonObject target,String permission,String tokenKey,String reasonKey){
        var approved=services.ManagerApprovalService.requestApproval(this,permission,"Sales task pricing","Reason for this price or discount:");
        if(approved==null)return false;target.addProperty(tokenKey,approved.lanApprovalToken());target.addProperty(reasonKey,approved.reason());return true;
    }
    private void attach(){JsonObject b=command("ATTACH");JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;var path=chooser.getSelectedFile().toPath();UiTaskRunner.submit(this,"task-file-read",()->{if(Files.size(path)>5242880)throw new IllegalArgumentException("File must be at most 5 MB.");return Base64.getEncoder().encodeToString(Files.readAllBytes(path));},encoded->{b.addProperty("filename",path.getFileName().toString());b.addProperty("content",encoded);send(b);},this::error);}
    private void download(){command("FILE");JsonArray files=selected.getAsJsonArray("files");if(files.isEmpty())throw new IllegalArgumentException("This task has no attachments.");List<Option> list=new ArrayList<>();for(JsonElement el:files){JsonObject f=el.getAsJsonObject();list.add(new Option(f.get("file_id").getAsInt(),str(f,"filename"),f));}int selectedFile=workspace.selectedAttachmentIndex();Option chosen=selectedFile>=0&&selectedFile<list.size()?list.get(selectedFile):(Option)JOptionPane.showInputDialog(this,"Attachment","Save Attachment",JOptionPane.PLAIN_MESSAGE,null,list.toArray(),list.get(0));if(chosen==null)return;JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File(chosen.label));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;var path=chooser.getSelectedFile().toPath();if(Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Replace the existing file?","Save Attachment",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;JsonObject b=command("FILE");b.addProperty("fileId",chosen.id);UiTaskRunner.submit(this,"task-file-save",()->{var r=LanApiClient.salesTasksRead(b);Files.write(path,Base64.getMimeDecoder().decode(r.get("content").getAsString()));return true;},ok->message.setText("Attachment saved."),this::error);}
    private void print(){if(selected==null)throw new IllegalArgumentException("Select a task.");JTextArea print=new JTextArea(slip(selected));print.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));print.setLineWrap(true);print.setWrapStyleWord(true);UiTaskRunner.submit(this,"task-print",()->print.print(),ok->{},this::error);}
    private String slip(JsonObject detail){JsonObject t=detail.getAsJsonObject("task");StringBuilder s=new StringBuilder("SALES TASK / CLAIM SLIP  ST-").append(str(t,"task_id")).append('\n').append(str(t,"customer_name")).append("  ").append(str(t,"phone")).append("\nDue: ").append(deadline(t)).append("\nWork: ").append(str(t,"status")).append("\n\n").append(str(t,"instructions")).append("\n\n");
        boolean finalized=str(t,"billing_status").equals("FINALIZED");s.append(finalized?"FINAL BILL":"ESTIMATES — NOT FINAL").append('\n');JsonObject checkout=JsonParser.parseString(str(t,"checkout_request")).getAsJsonObject();if(checkout.has("lines"))for(JsonElement el:checkout.getAsJsonArray("lines")){JsonObject l=el.getAsJsonObject();s.append(str(l,"miscItemName")).append("  Qty ").append(str(l,"quantity")).append(" × ").append(str(l,"unitPrice")).append('\n');}
        BigDecimal paid=detail.get("paid").getAsBigDecimal();s.append("Net payments: ").append(paid).append('\n');if(finalized){BigDecimal total=t.get("final_total").getAsBigDecimal();s.append("Final total: ").append(total).append('\n');if(List.of("COLLECTED","CANCELLED").contains(str(t,"status"))){s.append("Balance on task: 0.00\n");if("ACCOUNT".equals(str(detail,"salePaymentMethod")))s.append("Transferred to account: ").append(total.subtract(paid)).append('\n');}else s.append("Balance: ").append(total.subtract(paid)).append('\n');}else s.append("Balance: Not finalized\n");if(!str(t,"sale_id").isBlank())s.append("Linked sale: ").append(str(t,"sale_id")).append(" (see sale receipt for account settlement)\n");
        s.append("\nPAYMENT HISTORY\n");for(JsonElement el:detail.getAsJsonArray("payments")){JsonObject v=el.getAsJsonObject();s.append("P-").append(str(v,"payment_id")).append(" ").append(str(v,"created_at")).append(" ").append(str(v,"payment_action")).append(" ").append(str(v,"amount")).append(" ").append(str(v,"payment_method")).append(" ").append(str(v,"payment_reference")).append(" / ").append(str(v,"user_name")).append('\n');}
        s.append("\nACTIVITY\n");for(JsonElement el:detail.getAsJsonArray("events")){JsonObject v=el.getAsJsonObject();s.append(str(v,"created_at")).append(" ").append(str(v,"action")).append(" / ").append(str(v,"user_name")).append(" ").append(str(v,"reason")).append('\n');}s.append("\nATTACHMENTS\n");for(JsonElement el:detail.getAsJsonArray("files"))s.append(str(el.getAsJsonObject(),"filename")).append('\n');return s.toString();
    }
    private static String str(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    private static void merge(JsonObject to,JsonObject from){for(var e:from.entrySet())to.add(e.getKey(),e.getValue());}
    private static OffsetDateTime parseDate(String v){return OffsetDateTime.parse(v.replace(' ','T').replaceAll("([+-]\\d\\d)$","$1:00"));}
    private String deadline(JsonObject task){return task.has("due_asap")&&task.get("due_asap").getAsBoolean()?"ASAP":date(str(task,"due_at"));}
    private String date(String v){try{return v.isBlank()?"":parseDate(v).atZoneSameInstant(zone).format(DATE);}catch(Exception e){return v;}}
    private void error(Throwable e){message.setText("Action could not be completed.");JOptionPane.showMessageDialog(this,e.getMessage(),"Sales Tasks",JOptionPane.ERROR_MESSAGE);}
}
