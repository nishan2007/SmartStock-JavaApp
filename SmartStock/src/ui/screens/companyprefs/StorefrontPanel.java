package ui.screens.companyprefs;

import com.google.gson.*;
import services.LanApiClient;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.UUID;

/** Store-local online merchandising and pickup operations. Network work stays off the EDT. */
public final class StorefrontPanel extends JPanel {
    public static void openOrders(JFrame owner){
        if(!managers.PermissionManager.requirePermission("MAKE_SALE",owner,"Online pickup orders"))return;
        JDialog dialog=new JDialog(owner,"Online pickup orders",false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(new StorefrontPanel(true));
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setSize(Math.min(1100,screen.width-40),Math.min(700,screen.height-70));
        dialog.setLocationRelativeTo(owner);dialog.setVisible(true);
    }
    private final JLabel status=new JLabel("Loading online store…");
    private final JCheckBox enabled=new JCheckBox("Accept online pickup orders");
    private final JSpinner expiry=new JSpinner(new SpinnerNumberModel(48,1,8760,1));
    private final JTextField currency=new JTextField("GYD",5),welcome=new JTextField("Everyday essentials. A little extraordinary.",35);
    private final JTextField campaignTopic=new JTextField(35),campaignEyebrow=new JTextField(35),campaignHeadline=new JTextField(35),
        campaignSteps=new JTextField(35),campaignPrimary=new JTextField(35),campaignSecondary=new JTextField(35);
    private final JTextArea campaignDescription=new JTextArea(3,35);
    private final JComboBox<String> campaignVisual=new JComboBox<>(new String[]{"3D_PRINT","EDITORIAL"});
    private final JComboBox<String> campaignPrimaryAction=new JComboBox<>(new String[]{"START","EXPLORE","SHOP","MADE"});
    private final JComboBox<String> campaignSecondaryAction=new JComboBox<>(new String[]{"START","EXPLORE","SHOP","MADE"});
    private final DefaultTableModel productModel=model("ID","Product","POS price","Website price","Promo price","Published","Featured");
    private final DefaultTableModel orderModel=model("Order","Customer","Status","Total","Pickup deadline");
    private final JTable products=new JTable(productModel),orders=new JTable(orderModel);
    private final DefaultTableModel projectModel=model("Project","Title","Category","Status","Source order");
    private final JTable projects=new JTable(projectModel);
    private JsonArray projectData=new JsonArray();
    private final DefaultTableModel serviceModel=model("Service","Available at this store");
    private final JTable serviceTable=new JTable(serviceModel);
    private JsonArray serviceData=new JsonArray();
    private final DefaultTableModel quoteModel=model("Request","Topic","Email","Status","Date");
    private final JTable quoteRequests=new JTable(quoteModel);
    private JsonArray quoteData=new JsonArray();
    private final DefaultTableModel accessModel=model("Customer","Verified email","Status","Help needed");
    private final JTable access=new JTable(accessModel);
    private JsonArray accessData=new JsonArray();
    private JsonArray productData=new JsonArray(),orderData=new JsonArray();
    private boolean productsLoaded;
    private final boolean ordersOnly;
    public StorefrontPanel(){this(false);}
    public StorefrontPanel(boolean ordersOnly){
        super(new BorderLayout(12,12));this.ordersOnly=ordersOnly;setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        JPanel header=new JPanel(new BorderLayout());JLabel title=new JLabel("Deckers Online Store");title.setFont(title.getFont().deriveFont(Font.BOLD,23));header.add(title,BorderLayout.NORTH);header.add(status,BorderLayout.SOUTH);add(header,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();
        JPanel settings=new JPanel(new BorderLayout(12,12));JPanel fields=new JPanel(new GridLayout(0,2,10,15));fields.add(new JLabel("Store availability"));fields.add(enabled);fields.add(new JLabel("Pickup expiry in hours (48 = 2 days, 168 = 1 week)"));fields.add(expiry);fields.add(new JLabel("Currency code"));fields.add(currency);fields.add(new JLabel("Welcome message"));fields.add(welcome);settings.add(fields,BorderLayout.NORTH);
        JTextArea help=new JTextArea("The pickup timer starts when an order is marked READY. Existing orders keep their deadline and locked prices.\n\nConfigure the public HTTPS origin and named Cloudflare Tunnel on the store server. No server credentials are sent to registers.\n\nPhone-only customers need an email address added to their SmartStock account before they can activate website access.");help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);help.setOpaque(false);settings.add(help,BorderLayout.CENTER);
        JPanel settingsActions=new JPanel(new FlowLayout(FlowLayout.LEFT));settingsActions.add(button("Save settings",()->{JsonObject b=body("SETTINGS");b.addProperty("enabled",enabled.isSelected());b.addProperty("pickupHours",((Number)expiry.getValue()).intValue());b.addProperty("currency",currency.getText().trim().toUpperCase());b.addProperty("welcome",welcome.getText().trim());send(b);}));settingsActions.add(button("Resolve customer link",this::link));settings.add(settingsActions,BorderLayout.SOUTH);tabs.addTab("Settings",settings);
        JPanel campaign=new JPanel(new BorderLayout(10,10));JPanel campaignFields=new JPanel(new GridLayout(0,2,10,10));
        campaignFields.add(new JLabel("Campaign topic"));campaignFields.add(campaignTopic);
        campaignFields.add(new JLabel("Short label"));campaignFields.add(campaignEyebrow);
        campaignFields.add(new JLabel("Headline"));campaignFields.add(campaignHeadline);
        campaignDescription.setLineWrap(true);campaignDescription.setWrapStyleWord(true);
        campaignFields.add(new JLabel("Description"));campaignFields.add(new JScrollPane(campaignDescription));
        campaignFields.add(new JLabel("Process line"));campaignFields.add(campaignSteps);
        campaignFields.add(new JLabel("Primary button"));campaignFields.add(campaignPrimary);
        campaignFields.add(new JLabel("Secondary button"));campaignFields.add(campaignSecondary);
        campaignFields.add(new JLabel("Primary destination"));campaignFields.add(campaignPrimaryAction);
        campaignFields.add(new JLabel("Secondary destination"));campaignFields.add(campaignSecondaryAction);
        campaignFields.add(new JLabel("Visual style"));campaignFields.add(campaignVisual);
        campaign.add(campaignFields,BorderLayout.NORTH);
        JTextArea campaignHelp=new JTextArea("The featured homepage campaign is public. Save only approved marketing copy. START opens a custom request for the campaign topic. EXPLORE opens its service page, SHOP opens products, and MADE opens published projects. The 3D print visual is intended for the 3D Printing campaign. Editorial uses a general Deckers visual.");
        campaignHelp.setEditable(false);campaignHelp.setLineWrap(true);campaignHelp.setWrapStyleWord(true);campaignHelp.setOpaque(false);
        campaign.add(campaignHelp,BorderLayout.CENTER);
        JPanel campaignActions=new JPanel(new FlowLayout(FlowLayout.LEFT));campaignActions.add(button("Save featured campaign",this::saveCampaign));campaign.add(campaignActions,BorderLayout.SOUTH);
        tabs.addTab("Featured campaign",campaign);
        JPanel catalog=new JPanel(new BorderLayout(10,10));products.setAutoCreateRowSorter(true);catalog.add(new JScrollPane(products),BorderLayout.CENTER);
        JPanel publicationActions=new JPanel(new FlowLayout(FlowLayout.LEFT));publicationActions.add(button("Edit website publication",this::publish));publicationActions.add(button("Publish all active products",this::publishAll));catalog.add(publicationActions,BorderLayout.SOUTH);tabs.addTab("Products",catalog);
        JPanel serviceSettings=new JPanel(new BorderLayout(10,10));serviceTable.setAutoCreateRowSorter(true);serviceSettings.add(new JScrollPane(serviceTable),BorderLayout.CENTER);
        JTextArea serviceHelp=new JTextArea("Mark only services your store can currently accept. Customers can still see a service offered at another Deckers location.");serviceHelp.setEditable(false);serviceHelp.setLineWrap(true);serviceHelp.setWrapStyleWord(true);serviceHelp.setOpaque(false);serviceSettings.add(serviceHelp,BorderLayout.NORTH);
        JPanel serviceActions=new JPanel(new FlowLayout(FlowLayout.LEFT));serviceActions.add(button("Set availability",this::setServiceAvailability));serviceActions.add(button("Refresh",this::loadServices));serviceSettings.add(serviceActions,BorderLayout.SOUTH);
        if(managers.PermissionManager.hasPermission("COMPANY_PREFERENCES"))tabs.addTab("Services",serviceSettings);
        JPanel portfolio=new JPanel(new BorderLayout(10,10));projects.setAutoCreateRowSorter(true);portfolio.add(new JScrollPane(projects),BorderLayout.CENTER);
        JTextArea portfolioHelp=new JTextArea("Create a private draft from a completed custom order ID. Customer details, notes, artwork, and order price are never copied. Enter approved public copy and upload an approved cover photo before publishing.");portfolioHelp.setEditable(false);portfolioHelp.setLineWrap(true);portfolioHelp.setWrapStyleWord(true);portfolioHelp.setOpaque(false);portfolio.add(portfolioHelp,BorderLayout.NORTH);
        JPanel projectActions=new JPanel(new FlowLayout(FlowLayout.LEFT));projectActions.add(button("Add completed order",this::projectDraft));projectActions.add(button("Edit project",this::projectEdit));projectActions.add(button("Upload cover",this::projectCover));projectActions.add(button("Add photo",this::projectMediaUpload));projectActions.add(button("Manage photos",this::projectMediaManage));projectActions.add(button("Set status",this::projectStatus));projectActions.add(button("Prepare social post",this::projectShare));projectActions.add(button("Refresh",this::loadProjects));portfolio.add(projectActions,BorderLayout.SOUTH);tabs.addTab("Made at Deckers",portfolio);
        JPanel quoteQueue=new JPanel(new BorderLayout(10,10));quoteRequests.setAutoCreateRowSorter(true);quoteQueue.add(new JScrollPane(quoteRequests),BorderLayout.CENTER);
        JPanel quoteActions=new JPanel(new FlowLayout(FlowLayout.LEFT));quoteActions.add(button("View request",this::quoteDetails));quoteActions.add(button("Reference files",this::quoteFiles));quoteActions.add(button("Send design proof",this::quoteProof));quoteActions.add(button("Link SmartStock order",this::quoteLinkOrder));quoteActions.add(button("Update status",this::quoteStatus));quoteActions.add(button("Refresh",this::loadQuotes));quoteQueue.add(quoteActions,BorderLayout.SOUTH);if(managers.PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS"))tabs.addTab("Custom requests",quoteQueue);
        JPanel fulfillment=new JPanel(new BorderLayout(10,10));orders.setAutoCreateRowSorter(true);fulfillment.add(new JScrollPane(orders),BorderLayout.CENTER);JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(button("View order",this::details));for(String next:new String[]{"PREPARING","READY","NEEDS_ATTENTION","CANCELLED"})actions.add(button(next.replace('_',' '),()->transition(next)));actions.add(button("Collect and pay",this::collect));actions.add(button("Refresh",this::load));fulfillment.add(actions,BorderLayout.SOUTH);tabs.addTab("Online orders",fulfillment);
        JPanel customerAccess=new JPanel(new BorderLayout(10,10));access.setAutoCreateRowSorter(true);customerAccess.add(new JScrollPane(access),BorderLayout.CENTER);JPanel accessActions=new JPanel(new FlowLayout(FlowLayout.LEFT));accessActions.add(button("Resolve selected customer",()->{var selected=selected(access,accessData);if(selected!=null)link(value(selected,"email"));}));accessActions.add(button("Find by email",this::link));accessActions.add(button("Refresh",this::load));customerAccess.add(accessActions,BorderLayout.SOUTH);tabs.addTab("Customer access",customerAccess);
        if(ordersOnly){tabs.removeAll();tabs.addTab("Online orders",fulfillment);}
        add(tabs,BorderLayout.CENTER);load();if(!ordersOnly){loadProjects();if(managers.PermissionManager.hasPermission("COMPANY_PREFERENCES"))loadServices();if(managers.PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS"))loadQuotes();}
    }
    private static DefaultTableModel model(String...columns){return new DefaultTableModel(columns,0){public boolean isCellEditable(int r,int c){return false;}};}
    private JButton button(String text,Runnable action){JButton b=new JButton(text);DeckersSwing.styleUtilityButton(b,DeckersPalette.ORANGE);b.addActionListener(e->action.run());return b;}
    private static JsonObject body(String action){JsonObject b=new JsonObject();b.addProperty("action",action);return b;}
    private void load(){send(body(ordersOnly?"ORDERS_STATE":"STATE"));}
    private void send(JsonObject body){send(body,null);}
    private void send(JsonObject body,java.util.function.Consumer<JsonObject> onResult){status.setText("Working…");String key=UUID.randomUUID().toString();new SwingWorker<JsonObject,Void>(){
        protected JsonObject doInBackground()throws Exception{return LanApiClient.storefrontAdmin(body,key);}
        protected void done(){try{JsonObject result=get();if(onResult!=null){status.setText("Website account verified.");onResult.accept(result);}else if(result.has("orders")){render(result);status.setText(result.has("notifications")&&result.getAsJsonObject("notifications").get("failed").getAsInt()>0?"Order emails need attention. Open Email Outbox to review sender configuration or delivery errors.":"Store settings and orders are up to date.");}else{if(result.has("receiptNumber"))JOptionPane.showMessageDialog(StorefrontPanel.this,"Sale complete. Receipt "+result.get("receiptNumber").getAsString());load();}}catch(Exception e){status.setText("Could not complete the request.");JOptionPane.showMessageDialog(StorefrontPanel.this,e.getCause()==null?e.getMessage():e.getCause().getMessage(),"Online store",JOptionPane.ERROR_MESSAGE);}}
    }.execute();}
    private static String value(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    private void render(JsonObject data){var s=data.has("settings")?data.getAsJsonObject("settings"):new JsonObject();if(s.has("enabled")){enabled.setSelected(s.get("enabled").getAsBoolean());expiry.setValue(s.get("pickup_hours").getAsInt());currency.setText(value(s,"currency"));welcome.setText(value(s,"welcome"));
            campaignTopic.setText(value(s,"campaign_topic"));campaignEyebrow.setText(value(s,"campaign_eyebrow"));campaignHeadline.setText(value(s,"campaign_headline"));
            campaignDescription.setText(value(s,"campaign_description"));campaignSteps.setText(value(s,"campaign_steps"));
            campaignPrimary.setText(value(s,"campaign_primary"));campaignSecondary.setText(value(s,"campaign_secondary"));
            campaignVisual.setSelectedItem(value(s,"campaign_visual"));campaignPrimaryAction.setSelectedItem(value(s,"campaign_primary_action"));
            campaignSecondaryAction.setSelectedItem(value(s,"campaign_secondary_action"));}
        productData=data.has("products")?data.getAsJsonArray("products"):new JsonArray();productsLoaded=data.has("products");productModel.setRowCount(0);for(var e:productData){var p=e.getAsJsonObject();productModel.addRow(new Object[]{value(p,"id"),value(p,"name"),value(p,"price"),value(p,"website_price"),value(p,"promotional_price"),value(p,"published"),value(p,"featured")});}
        orderData=data.getAsJsonArray("orders");orderModel.setRowCount(0);for(var e:orderData){var o=e.getAsJsonObject();orderModel.addRow(new Object[]{value(o,"order_id"),value(o,"name"),value(o,"status"),value(o,"total"),value(o,"expires_at")});}
        accessData=data.has("enrollments")?data.getAsJsonArray("enrollments"):new JsonArray();accessModel.setRowCount(0);for(var e:accessData){var a=e.getAsJsonObject();accessModel.addRow(new Object[]{value(a,"name"),value(a,"email"),value(a,"status").replace('_',' '),value(a,"note")});}}
    private void saveCampaign(){JsonObject b=body("CAMPAIGN");b.addProperty("topic",campaignTopic.getText().trim());b.addProperty("eyebrow",campaignEyebrow.getText().trim());
        b.addProperty("headline",campaignHeadline.getText().trim());b.addProperty("description",campaignDescription.getText().trim());
        b.addProperty("steps",campaignSteps.getText().trim());b.addProperty("primary",campaignPrimary.getText().trim());
        b.addProperty("secondary",campaignSecondary.getText().trim());b.addProperty("visual",campaignVisual.getSelectedItem().toString());
        b.addProperty("primaryAction",campaignPrimaryAction.getSelectedItem().toString());
        b.addProperty("secondaryAction",campaignSecondaryAction.getSelectedItem().toString());send(b);}
    private void loadProjects(){send(body("PROJECT_STATE"),result->{projectData=result.getAsJsonArray("projects");projectModel.setRowCount(0);for(var e:projectData){var p=e.getAsJsonObject();projectModel.addRow(new Object[]{value(p,"id"),value(p,"title"),value(p,"category"),value(p,"status"),value(p,"sourceOrderId")});}status.setText("Projects are up to date.");});}
    private void loadServices(){send(body("SERVICE_STATE"),result->{serviceData=result.getAsJsonArray("services");serviceModel.setRowCount(0);for(var item:serviceData){var service=item.getAsJsonObject();serviceModel.addRow(new Object[]{value(service,"name"),service.get("available").getAsBoolean()?"Yes":"No"});}status.setText("Service availability is up to date.");});}
    private void setServiceAvailability(){var service=selected(serviceTable,serviceData);if(service==null)return;
        JCheckBox available=new JCheckBox("Accept new requests at this store",service.get("available").getAsBoolean());
        if(JOptionPane.showConfirmDialog(this,available,value(service,"name"),JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        JsonObject b=body("SERVICE_AVAILABILITY");b.addProperty("slug",value(service,"slug"));b.addProperty("available",available.isSelected());
        send(b,result->{status.setText("Service availability saved.");loadServices();});
    }
    private JsonObject selectedProject(){return selected(projects,projectData);}
    private void projectDraft(){String input=JOptionPane.showInputDialog(this,"Completed SmartStock custom order ID:");if(input==null)return;try{long id=Long.parseLong(input.trim());JsonObject b=body("PROJECT_DRAFT");b.addProperty("sourceOrderId",id);send(b,result->{status.setText("Private project draft created.");loadProjects();});}catch(NumberFormatException e){JOptionPane.showMessageDialog(this,"Enter a valid custom order ID.");}}
    private void projectEdit(){var p=selectedProject();if(p==null)return;if(serviceData.isEmpty()){JOptionPane.showMessageDialog(this,"The service list is still loading. Refresh the online store and try again.");return;}if(!productsLoaded){JOptionPane.showMessageDialog(this,"The product list is still loading. Refresh the online store and try again.");return;}String[] keys={"title","summary","category","materials","productionMethod","customization","tags","startingPrice"};String[] labels={"Project title","Short description","Category","Materials","Production method","Available customization","Tags (comma separated)","Starting price (blank for quote)"};JTextField[] fields=new JTextField[keys.length];JPanel form=new JPanel(new GridLayout(0,2,8,8));for(int i=0;i<keys.length;i++){String value=value(p,keys[i]);if(keys[i].equals("tags")&&p.has("tags")&&p.get("tags").isJsonArray()){java.util.List<String> tags=new java.util.ArrayList<>();for(var tag:p.getAsJsonArray("tags"))tags.add(tag.getAsString());value=String.join(", ",tags);}fields[i]=new JTextField(value,32);form.add(new JLabel(labels[i]));form.add(fields[i]);}
        java.util.List<Integer> ids=new java.util.ArrayList<>();DefaultListModel<String> choices=new DefaultListModel<>();
        java.util.Set<Integer> selectedIds=new java.util.HashSet<>();if(p.has("productIds")&&p.get("productIds").isJsonArray())for(var id:p.getAsJsonArray("productIds"))selectedIds.add(id.getAsInt());
        java.util.List<Integer> selectedRows=new java.util.ArrayList<>();
        for(var e:productData){var product=e.getAsJsonObject();if(!"true".equals(value(product,"published")))continue;
            int id=product.get("id").getAsInt();if(selectedIds.contains(id))selectedRows.add(ids.size());ids.add(id);choices.addElement(value(product,"name")+" (ID "+id+")");}
        JList<String> productChoices=new JList<>(choices);productChoices.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        productChoices.setSelectedIndices(selectedRows.stream().mapToInt(Integer::intValue).toArray());
        JScrollPane productScroll=new JScrollPane(productChoices);productScroll.setPreferredSize(new Dimension(340,140));
        form.add(new JLabel("Made using (published products)"));form.add(productScroll);
        java.util.List<String> serviceSlugs=new java.util.ArrayList<>();serviceSlugs.add("");
        java.util.List<String> serviceNames=new java.util.ArrayList<>();serviceNames.add("None / custom project");
        if(serviceData!=null)for(var item:serviceData){var service=item.getAsJsonObject();serviceSlugs.add(value(service,"slug"));serviceNames.add(value(service,"name"));}
        JComboBox<String> relatedService=new JComboBox<>(serviceNames.toArray(String[]::new));
        int currentService=serviceSlugs.indexOf(value(p,"serviceSlug"));relatedService.setSelectedIndex(Math.max(0,currentService));
        form.add(new JLabel("Related service"));form.add(relatedService);
        if(JOptionPane.showConfirmDialog(this,new JScrollPane(form),"Edit public project details",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;JsonObject b=body("PROJECT_SAVE");b.addProperty("projectId",value(p,"id"));for(int i=0;i<6;i++)b.addProperty(keys[i],fields[i].getText().trim());b.addProperty("serviceSlug",serviceSlugs.get(relatedService.getSelectedIndex()));JsonArray tags=new JsonArray();for(String tag:fields[6].getText().split(","))if(!tag.isBlank())tags.add(tag.trim());b.add("tags",tags);b.addProperty("startingPrice",fields[7].getText().trim());JsonArray related=new JsonArray();for(int index:productChoices.getSelectedIndices())related.add(ids.get(index));b.add("productIds",related);send(b,result->{status.setText("Project details saved.");loadProjects();});}
    private void projectCover(){var p=selectedProject();if(p==null)return;JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;java.io.File file=chooser.getSelectedFile();if(file.length()<1||file.length()>1_300_000){JOptionPane.showMessageDialog(this,"Choose a JPEG or PNG photo smaller than 1.3 MB.");return;}try{byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());JsonObject b=body("PROJECT_COVER");b.addProperty("projectId",value(p,"id"));b.addProperty("bytesBase64",java.util.Base64.getEncoder().encodeToString(bytes));send(b,result->{status.setText("Approved cover photo uploaded.");loadProjects();});}catch(java.io.IOException e){JOptionPane.showMessageDialog(this,"Could not read the selected photo.");}}
    private void projectMediaUpload(){var p=selectedProject();if(p==null)return;
        if(p.has("gallery")&&p.getAsJsonArray("gallery").size()>=6){JOptionPane.showMessageDialog(this,"A project can have at most six gallery photos.");return;}
        JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        java.io.File file=chooser.getSelectedFile();if(file.length()<1||file.length()>1_300_000){JOptionPane.showMessageDialog(this,"Choose a JPEG or PNG photo smaller than 1.3 MB.");return;}
        String[] roles={"FINAL","DETAIL","PROCESS","BEFORE","AFTER"};String role=(String)JOptionPane.showInputDialog(this,"How should this photo appear?","Project photo",JOptionPane.QUESTION_MESSAGE,null,roles,"FINAL");if(role==null)return;
        String caption=JOptionPane.showInputDialog(this,"Short public caption (optional):","");if(caption==null)return;
        try{byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());JsonObject b=body("PROJECT_MEDIA_UPLOAD");b.addProperty("projectId",value(p,"id"));b.addProperty("role",role);b.addProperty("caption",caption.trim());b.addProperty("bytesBase64",java.util.Base64.getEncoder().encodeToString(bytes));send(b,result->{status.setText("Project photo added.");loadProjects();});}
        catch(java.io.IOException e){JOptionPane.showMessageDialog(this,"Could not read the selected photo.");}
    }
    private void projectMediaManage(){var p=selectedProject();if(p==null)return;JsonArray gallery=p.has("gallery")?p.getAsJsonArray("gallery"):new JsonArray();
        if(gallery.isEmpty()){JOptionPane.showMessageDialog(this,"This project has no gallery photos yet.");return;}
        String[] labels=new String[gallery.size()];for(int i=0;i<gallery.size();i++){var item=gallery.get(i).getAsJsonObject();labels[i]=(i+1)+". "+value(item,"role")+" — "+value(item,"caption");}
        String chosen=(String)JOptionPane.showInputDialog(this,"Choose a photo to remove:","Project photos",JOptionPane.QUESTION_MESSAGE,null,labels,labels[0]);if(chosen==null)return;
        int index=java.util.Arrays.asList(labels).indexOf(chosen);if(index<0)return;
        if(JOptionPane.showConfirmDialog(this,"Remove this photo from the project?","Project photos",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        JsonObject b=body("PROJECT_MEDIA_DELETE");b.addProperty("projectId",value(p,"id"));b.addProperty("mediaId",value(gallery.get(index).getAsJsonObject(),"id"));send(b,result->{status.setText("Project photo removed.");loadProjects();});
    }
    private void projectStatus(){var p=selectedProject();if(p==null)return;String[] choices={"DRAFT","PUBLISHED","FEATURED","ARCHIVED"};String chosen=(String)JOptionPane.showInputDialog(this,"Choose website visibility:","Project status",JOptionPane.QUESTION_MESSAGE,null,choices,value(p,"status"));if(chosen==null)return;JsonObject b=body("PROJECT_STATUS");b.addProperty("projectId",value(p,"id"));b.addProperty("status",chosen);send(b,result->{status.setText("Project status updated.");loadProjects();});}
    private void projectShare(){var p=selectedProject();if(p==null)return;
        JsonObject b=body("PROJECT_SHARE");b.addProperty("projectId",value(p,"id"));send(b,result->{
            JsonObject share=result.getAsJsonObject("share");JTextArea caption=new JTextArea(value(share,"caption"),5,54);
            caption.setLineWrap(true);caption.setWrapStyleWord(true);
            if(JOptionPane.showConfirmDialog(this,new JScrollPane(caption),"Social post draft — review before sharing",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            try{
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(caption.getText()),null);
                status.setText("Social post text copied. Review the destination and image before publishing.");
            }catch(IllegalStateException e){JOptionPane.showMessageDialog(this,new JScrollPane(caption),"Clipboard busy — select and copy the text",JOptionPane.INFORMATION_MESSAGE);}
        });
    }
    private void loadQuotes(){send(body("QUOTE_STATE"),result->{quoteData=result.getAsJsonArray("quotes");quoteModel.setRowCount(0);for(var e:quoteData){var q=e.getAsJsonObject();quoteModel.addRow(new Object[]{value(q,"requestId"),value(q,"topic"),value(q,"email"),value(q,"status"),value(q,"createdAt")});}status.setText("Custom requests are up to date.");});}
    private void quoteDetails(){var q=selected(quoteRequests,quoteData);if(q==null)return;StringBuilder details=new StringBuilder("Request "+value(q,"requestId")+"\n"+value(q,"email")+"\n\n"+value(q,"topic")+"\n"+value(q,"description")+"\n\nQuantity: "+value(q,"quantity"));for(String key:new String[]{"size","color","material","contactPhone","desiredDate"})if(!value(q,key).isBlank())details.append("\n").append(key).append(": ").append(value(q,key));if(q.has("projectContext")&&q.get("projectContext").isJsonObject())details.append("\n\nPublished project context:\n").append(q.getAsJsonObject("projectContext"));JTextArea area=new JTextArea(details.toString(),17,58);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);JOptionPane.showMessageDialog(this,new JScrollPane(area),"Custom request",JOptionPane.INFORMATION_MESSAGE);}
    private void quoteStatus(){var q=selected(quoteRequests,quoteData);if(q==null)return;String[] choices={"REQUESTED","REVIEWING","AWAITING_ARTWORK","QUOTED","IN_PRODUCTION","READY","COMPLETED","CANCELLED"};String next=(String)JOptionPane.showInputDialog(this,"Customer-facing request status:","Custom request",JOptionPane.QUESTION_MESSAGE,null,choices,value(q,"status"));if(next==null)return;JsonObject b=body("QUOTE_STATUS");b.addProperty("requestId",value(q,"requestId"));b.addProperty("status",next);send(b,result->{status.setText("Request status updated.");loadQuotes();});}
    private void quoteLinkOrder(){var q=selected(quoteRequests,quoteData);if(q==null)return;String input=JOptionPane.showInputDialog(this,"Enter the existing SmartStock custom order ID for this customer's request:","Link production order",JOptionPane.QUESTION_MESSAGE);if(input==null)return;try{long order=Long.parseLong(input.trim());if(order<1)throw new NumberFormatException();JsonObject b=body("QUOTE_LINK_ORDER");b.addProperty("requestId",value(q,"requestId"));b.addProperty("customOrderId",order);send(b,result->{status.setText("Request linked to SmartStock order "+value(result.getAsJsonObject("link"),"orderNumber")+".");loadQuotes();});}catch(NumberFormatException e){JOptionPane.showMessageDialog(this,"Enter a valid SmartStock custom order ID.");}}
    private void quoteProof(){var q=selected(quoteRequests,quoteData);if(q==null)return;JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;java.io.File file=chooser.getSelectedFile();String filename=file.getName();if(file.length()<5||file.length()>4_194_304||!filename.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(jpg|jpeg|png|pdf)")){JOptionPane.showMessageDialog(this,"Choose a JPEG, PNG, or PDF proof up to 4 MB.");return;}
        if(JOptionPane.showConfirmDialog(this,"Send this design proof to the customer for approval? A previous pending proof will be superseded.","Design approval",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String requestId=value(q,"requestId"),proofId=UUID.randomUUID().toString();status.setText("Uploading private design proof…");new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());if(bytes.length>4_194_304)throw new IllegalArgumentException("Proofs must be 4 MB or smaller.");JsonObject b=body("QUOTE_PROOF");b.addProperty("requestId",requestId);b.addProperty("proofId",proofId);b.addProperty("filename",filename);b.addProperty("bytesBase64",java.util.Base64.getEncoder().encodeToString(bytes));return LanApiClient.storefrontAdmin(b,proofId);}
            protected void done(){try{get();status.setText("Design proof sent for approval.");loadQuotes();}catch(Exception e){status.setText("Could not send the design proof.");JOptionPane.showMessageDialog(StorefrontPanel.this,e.getCause()==null?e.getMessage():e.getCause().getMessage(),"Design approval",JOptionPane.ERROR_MESSAGE);}}
        }.execute();}
    private void quoteFiles(){var q=selected(quoteRequests,quoteData);if(q==null)return;JsonObject b=body("QUOTE_FILES");b.addProperty("requestId",value(q,"requestId"));send(b,result->{JsonArray files=result.getAsJsonArray("files");if(files.isEmpty()){JOptionPane.showMessageDialog(this,"This request has no reference files.");return;}String[] labels=new String[files.size()];for(int i=0;i<files.size();i++){var f=files.get(i).getAsJsonObject();labels[i]=value(f,"filename")+" ("+value(f,"byteSize")+" bytes)";}String selected=(String)JOptionPane.showInputDialog(this,"Choose a file to save:","Private reference files",JOptionPane.QUESTION_MESSAGE,null,labels,labels[0]);if(selected==null)return;int index=java.util.Arrays.asList(labels).indexOf(selected);if(index<0)return;JsonObject file=files.get(index).getAsJsonObject();downloadQuoteFile(UUID.fromString(value(file,"fileId")));});}
    private void downloadQuoteFile(UUID id){status.setText("Loading private file…");new SwingWorker<JsonObject,Void>(){
        protected JsonObject doInBackground()throws Exception{return LanApiClient.storefrontQuoteFile(id);}
        protected void done(){try{JsonObject data=get().getAsJsonObject("file");JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File(value(data,"filename")));if(chooser.showSaveDialog(StorefrontPanel.this)!=JFileChooser.APPROVE_OPTION)return;java.nio.file.Files.write(chooser.getSelectedFile().toPath(),java.util.Base64.getDecoder().decode(value(data,"bytesBase64")));status.setText("Reference file saved.");}
            catch(Exception e){status.setText("Could not load the reference file.");JOptionPane.showMessageDialog(StorefrontPanel.this,e.getCause()==null?e.getMessage():e.getCause().getMessage(),"Custom request",JOptionPane.ERROR_MESSAGE);}}
    }.execute();}
    private JsonObject selected(JTable table,JsonArray source){int row=table.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(this,"Select a row first.");return null;}return source.get(table.convertRowIndexToModel(row)).getAsJsonObject();}
    private void publish(){
        var p=selected(products,productData);if(p==null)return;
        JCheckBox published=new JCheckBox("Published on website",p.get("published").getAsBoolean()),
            featured=new JCheckBox("Feature on homepage",p.get("featured").getAsBoolean());
        JTextField websitePrice=new JTextField(value(p,"website_price"),14),
            promotionalPrice=new JTextField(value(p,"promotional_price"),14);
        JTextArea description=new JTextArea(value(p,"description"),5,35);
        description.setLineWrap(true);description.setWrapStyleWord(true);
        JPanel form=new JPanel(new BorderLayout(8,8)),checks=new JPanel(),prices=new JPanel(new GridLayout(0,2,8,8));
        checks.add(published);checks.add(featured);form.add(checks,BorderLayout.NORTH);
        prices.add(new JLabel("POS price (reference only)"));prices.add(new JLabel(value(p,"price")));
        prices.add(new JLabel("Website price (blank uses POS price)"));prices.add(websitePrice);
        prices.add(new JLabel("Promotional price (optional)"));prices.add(promotionalPrice);
        JPanel details=new JPanel(new BorderLayout(8,8));details.add(prices,BorderLayout.NORTH);
        details.add(new JScrollPane(description),BorderLayout.CENTER);form.add(details,BorderLayout.CENTER);
        if(JOptionPane.showConfirmDialog(this,form,value(p,"name"),JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        var b=body("PUBLISH");b.add("productId",p.get("id"));b.addProperty("published",published.isSelected());
        b.addProperty("featured",featured.isSelected());b.addProperty("description",description.getText());
        b.addProperty("websitePrice",websitePrice.getText().trim());b.addProperty("promotionalPrice",promotionalPrice.getText().trim());
        send(b);
    }
    private void publishAll(){
        if(JOptionPane.showConfirmDialog(this,"Publish all currently active inventory products for this store?\nArchived products and services are excluded. Existing descriptions and featured selections are preserved.\nProducts added later must be published separately.","Publish all active products",JOptionPane.OK_CANCEL_OPTION,JOptionPane.QUESTION_MESSAGE)!=JOptionPane.OK_OPTION)return;
        send(body("PUBLISH_ALL_ACTIVE"),result->{JOptionPane.showMessageDialog(this,result.get("publishedCount").getAsInt()+" additional products published.");load();});
    }
    private void details(){var o=selected(orders,orderData);if(o==null)return;var q=o.getAsJsonObject("quote");StringBuilder text=new StringBuilder("Order "+value(o,"order_id")+"\n"+value(o,"name")+" — "+value(o,"email")+"\n\n");for(var e:q.getAsJsonArray("lines")){var l=e.getAsJsonObject();text.append(value(l,"quantity")).append(" × ").append(value(l,"name")).append(" @ ").append(value(l,"price")).append('\n');}text.append("\nLocked total: ").append(value(o,"total")).append("\n").append(value(o,"note"));JTextArea area=new JTextArea(text.toString(),14,55);area.setEditable(false);JOptionPane.showMessageDialog(this,new JScrollPane(area),"Pickup order",JOptionPane.INFORMATION_MESSAGE);}
    private void transition(String state){var o=selected(orders,orderData);if(o==null)return;String note=JOptionPane.showInputDialog(this,"Note for "+state.replace('_',' ')+":",value(o,"note"));if(note==null)return;var b=body("TRANSITION");b.add("orderId",o.get("order_id"));b.addProperty("status",state);b.addProperty("note",note);send(b);}
    private void collect(){var o=selected(orders,orderData);if(o==null)return;JComboBox<String> method=new JComboBox<>(new String[]{"CASH","CARD","MMG","CHEQUE","BANK_TRANSFER"});JTextField cash=new JTextField(value(o,"total")),reference=new JTextField();JPanel form=new JPanel(new GridLayout(0,2,8,10));form.add(new JLabel("Payment method"));form.add(method);form.add(new JLabel("Amount collected"));form.add(cash);form.add(new JLabel("Payment reference"));form.add(reference);
        if(JOptionPane.showConfirmDialog(this,form,"Collect and pay — "+value(o,"total"),JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        try{var b=body("COLLECT");b.add("orderId",o.get("order_id"));b.addProperty("paymentMethod",method.getSelectedItem().toString());b.addProperty("cashCollected",new java.math.BigDecimal(cash.getText().trim()));b.addProperty("paymentReference",reference.getText().trim());send(b);}catch(NumberFormatException e){JOptionPane.showMessageDialog(this,"Enter a valid amount.");}}
    private void link(){
        String email=JOptionPane.showInputDialog(this,"Enter the email the customer verified on the Deckers website:","Find website account",JOptionPane.QUESTION_MESSAGE);
        if(email==null||email.isBlank())return;link(email);
    }
    private void link(String email){
        var request=body("LINK_SEARCH");request.addProperty("email",email.trim());send(request,this::chooseCustomerLink);
    }
    private void chooseCustomerLink(JsonObject result){
        JsonObject identity=result.getAsJsonObject("identity");JsonArray candidates=result.getAsJsonArray("customers");
        if(candidates.isEmpty()){JOptionPane.showMessageDialog(this,"No active SmartStock customer has this email. Update the correct customer record's email first, then search again.","Customer record needed",JOptionPane.INFORMATION_MESSAGE);return;}
        DefaultTableModel model=model("Account","Customer","Email","Phone","Balance","Website access");
        for(var e:candidates){var c=e.getAsJsonObject();model.addRow(new Object[]{value(c,"number"),value(c,"name"),value(c,"email"),value(c,"phone"),value(c,"balance"),c.get("linked").getAsBoolean()?"Already linked":"Not linked"});}
        JTable table=new JTable(model);table.setAutoCreateRowSorter(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setPreferredScrollableViewportSize(new Dimension(780,200));
        JPanel form=new JPanel(new BorderLayout(10,10));form.add(new JLabel("Verified website email: "+value(identity,"email")),BorderLayout.NORTH);form.add(new JScrollPane(table),BorderLayout.CENTER);
        form.add(new JLabel("Select the customer's record. Other records remain separate; balances and sales are not merged."),BorderLayout.SOUTH);
        while(JOptionPane.showOptionDialog(this,form,"Choose customer account",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Link selected customer","Cancel"},"Cancel")==JOptionPane.OK_OPTION){
            int row=table.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(this,"Select the correct customer record first.");continue;}
            JsonObject customer=candidates.get(table.convertRowIndexToModel(row)).getAsJsonObject();var b=body("LINK");b.add("authId",identity.get("authId"));b.add("email",identity.get("email"));b.add("customerUuid",customer.get("uuid"));send(b);return;
        }
    }
}
