package ui.helpers;

import com.google.gson.*;
import services.BalanceSheetService;
import java.awt.*;
import java.awt.event.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;

/** Captures raw Swing form values without posting incomplete accounting entries. */
public final class BalanceSheetDraftTracker implements AutoCloseable {
    private final JFrame owner;
    private final Supplier<JsonObject> range;
    private final JLabel status=new JLabel("History: connecting…");
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"balance-sheet-draft-history");t.setDaemon(true);return t;});
    private final Deque<JsonObject> pending=new ArrayDeque<>();
    private volatile JsonObject workspace;
    private volatile Exception failure;
    private Form active,last;
    private Form persistent;
    private boolean dialogOpen;
    private final javax.swing.Timer poll;
    private boolean polling;
    private static final class Form {
        final String id=UUID.randomUUID().toString(),kind;
        final Object component;
        final List<Runnable> detach=new ArrayList<>();
        final IdentityHashMap<Object,String> rowIds=new IdentityHashMap<>();
        final javax.swing.Timer debounce,maximum;
        long revision;
        String workspaceId;
        boolean persistent;
        Form(String kind,Object component,Runnable capture){this.kind=kind;this.component=component;debounce=new javax.swing.Timer(500,e->capture.run());debounce.setRepeats(false);maximum=new javax.swing.Timer(2000,e->capture.run());maximum.setRepeats(false);}
    }
    public BalanceSheetDraftTracker(JFrame owner,Supplier<JsonObject> range,java.util.function.Consumer<JsonObject> refresh) {
        this.owner=owner;this.range=range;
        poll=new javax.swing.Timer(2000,e->{
            if(polling||dialogOpen||workspace==null||!owner.isShowing())return;
            polling=true;JsonObject request=new JsonObject();request.addProperty("workspaceId",workspace.get("workspaceId").getAsString());
            worker.submit(()->{try{drain();JsonObject updated=BalanceSheetService.workspace("POLL",request);long old=workspace.get("revision").getAsLong();workspace=updated;
                SwingUtilities.invokeLater(()->{if(updated.get("revision").getAsLong()!=old)refresh.accept(updated);setStatus("Saved");});
            }catch(Exception ex){failure=ex;setStatus("Disconnected · Unsaved input retained");}finally{SwingUtilities.invokeLater(()->polling=false);}});
        });poll.start();
        owner.addWindowListener(new WindowAdapter(){@Override public void windowClosed(WindowEvent e){close();}});
    }
    public JLabel statusLabel(){return status;}
    public void setWorkspace(JsonObject value){workspace=value;setStatus("Saved");}
    public void watchCashChecks(Component... components){
        if(persistent!=null){persistent.debounce.stop();persistent.maximum.stop();persistent.detach.forEach(Runnable::run);}
        final Form[] holder=new Form[1];Form form=new Form("CASH_CHECKS",components,()->capture(holder[0],"INPUT","editing"));holder[0]=form;
        form.persistent=true;form.workspaceId=workspace.get("workspaceId").getAsString();persistent=form;for(Component c:components)attach(c,form);
    }
    public void captureCashChecks(){if(persistent!=null)capture(persistent,"LEAVE","editing");}
    public boolean isDialogOpen(){return dialogOpen;}
    public void showHistory(){
        if(workspace==null)return;
        JDialog dialog=new JDialog(owner,"Balance Sheet History",true);
        JPanel panel=new JPanel(new BorderLayout(10,10));panel.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
        JTextField employee=new JTextField(16),date=new JTextField(10);JComboBox<String> action=new JComboBox<>(new String[]{"","INPUT","ADD","REMOVE","CLEAR","CANCEL","COMMIT","SUBMIT","REVISE","SYSTEM_REFRESH"});
        JPanel filters=new JPanel(new FlowLayout(FlowLayout.LEFT));JButton apply=new JButton("Apply filters"),more=new JButton("Older events"),compare=new JButton("Compare selected");
        filters.add(new JLabel("Employee"));filters.add(employee);filters.add(new JLabel("Date (yyyy-MM-dd)"));filters.add(date);filters.add(action);filters.add(apply);
        DefaultListModel<JsonObject> model=new DefaultListModel<>();JList<JsonObject> events=new JList<>(model);events.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        events.setCellRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
            JsonObject row=(JsonObject)value;return super.getListCellRendererComponent(list,"Revision "+row.get("revision")+" · "+row.get("eventType").getAsString()+" · "+row.get("employee").getAsString()+" · Received "+row.get("receivedAt").getAsString(),index,selected,focus);
        }});
        JTextArea snapshot=new JTextArea();snapshot.setEditable(false);snapshot.setLineWrap(true);snapshot.setWrapStyleWord(true);
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(events),new JScrollPane(snapshot));split.setResizeWeight(.4);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));JButton inspect=new JButton("Open snapshot");buttons.add(more);buttons.add(inspect);buttons.add(compare);
        String workspaceId=workspace.get("workspaceId").getAsString();final long[] cursor={Long.MAX_VALUE};
        java.util.function.Consumer<Boolean> load=older->{
            JsonObject request=new JsonObject();request.addProperty("workspaceId",workspaceId);request.addProperty("employee",employee.getText().trim());request.addProperty("date",date.getText().trim());request.addProperty("eventType",Objects.toString(action.getSelectedItem(),""));if(older)request.addProperty("before",cursor[0]);
            JsonObject result=await("Loading balance sheet history…",()->BalanceSheetService.workspace("HISTORY",request));if(result==null)return;
            if(!older)model.clear();for(var row:result.getAsJsonArray("events")){model.addElement(row.getAsJsonObject());cursor[0]=row.getAsJsonObject().get("eventId").getAsLong();}more.setEnabled(result.get("hasMore").getAsBoolean());
        };
        inspect.addActionListener(e->{JsonObject row=events.getSelectedValue();if(row==null)return;JsonObject request=new JsonObject();request.addProperty("workspaceId",workspaceId);request.add("eventId",row.get("eventId"));JsonObject result=await("Opening snapshot…",()->BalanceSheetService.workspace("EVENT",request));if(result!=null)snapshot.setText(new GsonBuilder().setPrettyPrinting().create().toJson(result));snapshot.setCaretPosition(0);});
        compare.addActionListener(e->{List<JsonObject> rows=events.getSelectedValuesList();if(rows.size()!=2){snapshot.setText("Select two history events to compare.");return;}
            JsonObject result=await("Comparing snapshots…",()->{JsonObject pair=new JsonObject();for(int i=0;i<2;i++){JsonObject request=new JsonObject();request.addProperty("workspaceId",workspaceId);request.add("eventId",rows.get(i).get("eventId"));pair.add(i==0?"selected":"compared",BalanceSheetService.workspace("EVENT",request).get("after"));}return pair;});
            if(result!=null){StringBuilder changes=new StringBuilder();diff(result.get("compared"),result.get("selected"),"sheet",changes);snapshot.setText(changes.length()==0?"No changes":changes.toString());snapshot.setCaretPosition(0);}
        });
        apply.addActionListener(e->load.accept(false));more.addActionListener(e->load.accept(true));panel.add(filters,BorderLayout.NORTH);panel.add(split,BorderLayout.CENTER);panel.add(buttons,BorderLayout.SOUTH);dialog.setContentPane(panel);dialog.setSize(1000,720);dialog.setLocationRelativeTo(owner);load.accept(false);dialog.setVisible(true);
    }
    private static void diff(JsonElement before,JsonElement after,String path,StringBuilder out){
        if(Objects.equals(before,after))return;
        if(before!=null&&after!=null&&before.isJsonObject()&&after.isJsonObject()){Set<String> keys=new TreeSet<>(before.getAsJsonObject().keySet());keys.addAll(after.getAsJsonObject().keySet());for(String key:keys)diff(before.getAsJsonObject().get(key),after.getAsJsonObject().get(key),path+" / "+key,out);return;}
        out.append(path).append("\nBefore: ").append(before==null?"Absent":before).append("\nAfter: ").append(after==null?"Removed":after).append("\n\n");
    }
    private void setStatus(String value){SwingUtilities.invokeLater(()->status.setText("History: "+value));}
    public int confirm(Component parent,Object message,String title,int option,int type) {
        if(!(message instanceof Component))return JOptionPane.showConfirmDialog(parent,message,title,option,type);
        captureCashChecks();
        JsonObject selected=range.get();
        JsonObject opened=await("Opening captured balance sheet entry…",()->{
            drain();return BalanceSheetService.workspace("OPEN",selected);
        });
        if(opened==null)return JOptionPane.CANCEL_OPTION;workspace=opened;dialogOpen=true;
        final Form[] holder=new Form[1];Form form=new Form(title,message,()->capture(holder[0],"INPUT","editing"));holder[0]=form;form.workspaceId=opened.get("workspaceId").getAsString();active=form;
        attach((Component)message,form);capture(form,"ADD","editing");
        int result;
        try{result=JOptionPane.showConfirmDialog(parent,message,title,option,type);
            capture(form,result==JOptionPane.OK_OPTION?"LEAVE":"CANCEL",result==JOptionPane.OK_OPTION?"editing":"cancelled");
            Boolean saved=await("Saving input history…",()->{drain();return true;});
            if(saved==null){JOptionPane.showMessageDialog(owner,"The input history could not be saved. This accounting change was not posted; captured input remains queued in this window.","Balance Sheet",JOptionPane.WARNING_MESSAGE);return JOptionPane.CANCEL_OPTION;}
            last=form;return result;
        }finally{form.debounce.stop();form.maximum.stop();form.detach.forEach(Runnable::run);active=null;dialogOpen=false;}
    }
    public int confirm(Component parent,Object message,String title,int option){return confirm(parent,message,title,option,JOptionPane.QUESTION_MESSAGE);}
    private <T>T await(String message,Callable<T> task){
        try{return ResponsiveTask.await(owner,message,task);}
        catch(Exception ex){failure=ex;setStatus("Unsaved · "+ex.getMessage());JOptionPane.showMessageDialog(owner,ex.getMessage(),"Input history unavailable",JOptionPane.WARNING_MESSAGE);return null;}
    }
    /** Called on the accounting worker immediately before one financial operation. */
    public void prepareMutation()throws Exception {
        drain();if(failure!=null)throw failure;
        if(workspace==null)throw new IllegalStateException("Open the current Balance Sheet before saving.");
        JsonObject context=new JsonObject();context.addProperty("workspaceId",workspace.get("workspaceId").getAsString());context.addProperty("expectedRevision",workspace.get("revision").getAsLong());
        if(last!=null)context.addProperty("formId",last.id);BalanceSheetService.useWorkspaceForNextChange(context);
    }
    private void changed(Form form,String type){
        if(!"INPUT".equals(type)){capture(form,type,"editing");return;}
        form.debounce.restart();if(!form.maximum.isRunning())form.maximum.start();setStatus("Unsaved");
    }
    private void attach(Component component,Form form){
        if(component instanceof JTextComponent field&&field.isEditable()) {
            DocumentListener listener=new DocumentListener(){private void update(){if(!form.persistent||field.isFocusOwner())changed(form,"INPUT");}public void insertUpdate(DocumentEvent e){update();}public void removeUpdate(DocumentEvent e){update();}public void changedUpdate(DocumentEvent e){update();}};
            field.getDocument().addDocumentListener(listener);form.detach.add(()->field.getDocument().removeDocumentListener(listener));
            FocusListener focus=new FocusAdapter(){@Override public void focusLost(FocusEvent e){capture(form,"LEAVE","editing");}};field.addFocusListener(focus);form.detach.add(()->field.removeFocusListener(focus));
        }
        if(component instanceof JComboBox<?> box){ActionListener listener=e->changed(form,"INPUT");box.addActionListener(listener);form.detach.add(()->box.removeActionListener(listener));}
        if(component instanceof JTable table) {
            TableModelListener listener=e->{if(!form.persistent||e.getType()==TableModelEvent.UPDATE&&e.getColumn()==1&&(table.isEditing()||table.isFocusOwner()))changed(form,e.getType()==TableModelEvent.DELETE?"REMOVE":e.getType()==TableModelEvent.INSERT?"ADD":"INPUT");};table.getModel().addTableModelListener(listener);form.detach.add(()->table.getModel().removeTableModelListener(listener));
            java.beans.PropertyChangeListener editor=e->{if("tableCellEditor".equals(e.getPropertyName())&&table.isEditing()&&table.getEditorComponent()!=null)attach(table.getEditorComponent(),form);};table.addPropertyChangeListener(editor);form.detach.add(()->table.removePropertyChangeListener(editor));
        }
        if(component instanceof Container container)for(Component child:container.getComponents())attach(child,form);
    }
    private void capture(Form form,String type,String status){
        if(form==null)return;form.debounce.stop();form.maximum.stop();
        JsonObject values=new JsonObject();
        if(form.component instanceof Component[] components){int i=0;for(Component c:components)collect(c,"cash checks."+(i++),values,form);}
        else collect((Component)form.component,"form",values,form);
        JsonObject content=new JsonObject();content.addProperty("kind",form.kind);content.addProperty("status",status);content.add("values",values);
        JsonObject event=new JsonObject();event.addProperty("workspaceId",form.workspaceId);event.addProperty("formId",form.id);event.addProperty("formRevision",form.revision++);event.addProperty("eventKey",UUID.randomUUID().toString());event.addProperty("eventType",type);event.addProperty("capturedAt",Instant.now().toString());event.add("content",content);
        synchronized(pending){pending.add(event);}setStatus("Unsaved");worker.submit(()->{try{drain();}catch(Exception ex){failure=ex;setStatus("Disconnected · Unsaved input retained");}});
    }
    private static void collect(Component c,String path,JsonObject out,Form form){
        if(c instanceof JTextComponent field&&field.isEditable()){out.addProperty(path,field.getText());return;}
        if(c instanceof JComboBox<?> box){out.addProperty(path,Objects.toString(box.isEditable()?box.getEditor().getItem():box.getSelectedItem(),""));return;}
        if(c instanceof JTable table){JsonArray rows=new JsonArray();for(int r=0;r<table.getModel().getRowCount();r++){JsonObject row=new JsonObject();Object key=table.getModel() instanceof DefaultTableModel m?m.getDataVector().get(r):Integer.valueOf(r);row.addProperty("rowId",form.rowIds.computeIfAbsent(key,k->UUID.randomUUID().toString()));for(int col=0;col<table.getModel().getColumnCount();col++)row.addProperty(table.getModel().getColumnName(col),Objects.toString(table.getModel().getValueAt(r,col),""));if(table.isEditing()&&table.getEditingRow()==r&&table.getEditorComponent() instanceof JTextComponent text)row.addProperty("editing "+table.getColumnName(table.getEditingColumn()),text.getText());rows.add(row);}out.add(path,rows);return;}
        if(c instanceof Container container){int i=0;String label="";for(Component child:container.getComponents()){if(child instanceof JLabel l)label=l.getText();collect(child,path+"."+(i++)+(label.isBlank()?"":" "+label),out,form);}}
    }
    private synchronized void drain()throws Exception {
        while(true){JsonObject next;synchronized(pending){next=pending.peek();}if(next==null){failure=null;return;}
            JsonObject saved=BalanceSheetService.workspace("CAPTURE",next);if(workspace!=null&&workspace.get("workspaceId").getAsString().equals(next.get("workspaceId").getAsString()))workspace.addProperty("revision",saved.get("revision").getAsLong());
            synchronized(pending){pending.remove();}failure=null;setStatus("Saved");
        }
    }
    @Override public void close(){poll.stop();if(active!=null)capture(active,"LEAVE","editing");if(persistent!=null){capture(persistent,"LEAVE","editing");persistent.debounce.stop();persistent.maximum.stop();persistent.detach.forEach(Runnable::run);}worker.shutdown();}
}
