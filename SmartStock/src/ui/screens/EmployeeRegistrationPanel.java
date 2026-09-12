package ui.screens;

import services.EmployeeRegistrationService.Registration;
import services.LanApiClient;
import com.google.gson.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Applicant review stays inside the existing employee permission boundary. */
final class EmployeeRegistrationPanel extends JPanel {
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"Submitted","Name","Email","Phone","Status"},0){public boolean isCellEditable(int r,int c){return false;}};
    private final JTable table=new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter=new TableRowSorter<>(model);
    private final JComboBox<String> statusFilter=new JComboBox<>(new String[]{"All applications","Submitted","Under review","Interview","Information requested","Hired","Not selected","Withdrawn","Hiring in progress"});
    private final JButton details=new JButton("View application"),review=new JButton("Update status"),refresh=new JButton("Refresh");
    private final JLabel status=new JLabel("Select an application to review.");
    private final Consumer<Registration> selected;
    private List<Registration> rows=List.of();
    private String search="";
    private long generation;
    EmployeeRegistrationPanel(Consumer<Registration> selected,Consumer<JTable> styleTable){
        super(new BorderLayout(6,6));this.selected=selected;setOpaque(false);
        table.setRowSorter(sorter);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);styleTable.accept(table);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEADING,4,4));controls.setOpaque(false);
        controls.add(statusFilter);controls.add(refresh);controls.add(details);controls.add(review);add(controls,BorderLayout.NORTH);add(new JScrollPane(table),BorderLayout.CENTER);add(status,BorderLayout.SOUTH);
        details.setEnabled(false);review.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()){Registration r=selection();details.setEnabled(r!=null);review.setEnabled(r!=null&&!Set.of("APPROVED","APPROVING").contains(r.status()));selected.accept(r);}});
        refresh.addActionListener(e->reload());statusFilter.addActionListener(e->filter(search));details.addActionListener(e->showDetails());review.addActionListener(e->review());
    }
    private Registration selection(){int i=table.getSelectedRow();return i<0?null:rows.get(table.convertRowIndexToModel(i));}
    void clearSelection(){table.clearSelection();}
    void filter(String text){search=text;String chosen=(String)statusFilter.getSelectedItem();sorter.setRowFilter(new RowFilter<DefaultTableModel,Integer>(){public boolean include(Entry<? extends DefaultTableModel,? extends Integer> e){boolean match=false;for(int i=0;i<e.getValueCount();i++)match|=e.getStringValue(i).toLowerCase(java.util.Locale.ROOT).contains(text.toLowerCase(java.util.Locale.ROOT));return match&&(statusFilter.getSelectedIndex()==0||e.getStringValue(4).equals(chosen));}});}
    void reload(){long ticket=++generation;status.setText("Loading applications…");refresh.setEnabled(false);
        new SwingWorker<List<Registration>,Void>(){
            protected List<Registration> doInBackground()throws Exception{return LanApiClient.employeeRegistrations(true);}
            protected void done(){if(ticket!=generation)return;refresh.setEnabled(true);try{rows=get();table.clearSelection();model.setRowCount(0);for(var r:rows)model.addRow(new Object[]{r.submittedAt(),r.firstName()+" "+r.lastName(),r.email(),r.phone(),display(r.status())});status.setText(rows.isEmpty()?"No submitted applications.":"Select an application. Hire using the employee setup fields.");filter(search);}catch(Exception ex){status.setText("Could not load applications.");JOptionPane.showMessageDialog(EmployeeRegistrationPanel.this,error(ex));}}
        }.execute();
    }
    private void review(){Registration r=selection();if(r==null)return;
        JComboBox<String> next=new JComboBox<>(new String[]{"Under review","Interview","Information requested","Not selected"});
        if(Set.of("REJECTED","WITHDRAWN").contains(r.status())){next.setSelectedIndex(2);next.setEnabled(false);}
        JTextArea message=new JTextArea(4,35),note=new JTextArea(4,35);message.setLineWrap(true);note.setLineWrap(true);
        JPanel panel=new JPanel(new GridLayout(0,1,4,4));panel.add(new JLabel("Application status"));panel.add(next);panel.add(new JLabel("Message visible to the applicant (required when requesting information)"));panel.add(new JScrollPane(message));panel.add(new JLabel("Internal note — only employee managers can see this"));panel.add(new JScrollPane(note));
        if(JOptionPane.showConfirmDialog(this,panel,"Review application",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String target=new String[]{"UNDER_REVIEW","INTERVIEW","INFORMATION_REQUESTED","REJECTED"}[next.getSelectedIndex()];
        if(target.equals("INFORMATION_REQUESTED")&&message.getText().isBlank()){JOptionPane.showMessageDialog(this,"Explain what the applicant should update.");return;}
        JsonObject body=new JsonObject();body.addProperty("status",target);body.addProperty("message",message.getText());body.addProperty("note",note.getText());
        review.setEnabled(false);new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{LanApiClient.employeeApplicationRequest("REVIEW",r.id(),body);return null;}protected void done(){try{get();}catch(Exception e){JOptionPane.showMessageDialog(EmployeeRegistrationPanel.this,error(e));}reload();}}.execute();
    }
    private void showDetails(){Registration row=selection();if(row==null)return;details.setEnabled(false);
        new SwingWorker<JsonObject,Void>(){protected JsonObject doInBackground()throws Exception{return LanApiClient.employeeApplicationRequest("DETAILS",row.id(),null).getAsJsonObject("application");}
            protected void done(){details.setEnabled(true);try{showApplication(row,get());}catch(Exception e){JOptionPane.showMessageDialog(EmployeeRegistrationPanel.this,error(e));}}
        }.execute();
    }
    private void showApplication(Registration row,JsonObject app){
        JTextArea text=new JTextArea(24,65);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);StringBuilder b=new StringBuilder(row.firstName()+" "+row.lastName()+"\n"+row.email()+"\nStatus: "+display(row.status())+"\n\n");
        app.getAsJsonObject("form").entrySet().forEach(e->b.append(e.getKey().replaceAll("([a-z])([A-Z])","$1 $2")).append(": ").append(e.getValue().isJsonNull()?"":e.getValue().getAsString()).append("\n\n"));
        b.append("REVIEW HISTORY\n");for(JsonElement el:app.getAsJsonArray("history")){JsonObject event=el.getAsJsonObject();b.append(event.get("at").getAsString()).append(" · ").append(display(event.get("status").getAsString())).append("\nApplicant message: ").append(event.get("message").getAsString()).append("\nInternal note: ").append(event.get("internalNote").getAsString()).append("\n\n");}text.setText(b.toString());text.setCaretPosition(0);
        JPanel panel=new JPanel(new BorderLayout(8,8));panel.add(new JScrollPane(text),BorderLayout.CENTER);JPanel files=new JPanel(new GridLayout(0,1,4,4));
        for(JsonElement el:app.getAsJsonArray("attachments")){JsonObject file=el.getAsJsonObject();JButton open=new JButton(file.get("filename").getAsString()+" · "+file.get("category").getAsString());open.addActionListener(e->openDocument(row.id(),UUID.fromString(file.get("id").getAsString()),open));files.add(open);}
        if(row.documentUrl()!=null&&!row.documentUrl().isBlank()){JButton legacy=new JButton("Open existing ID document");legacy.addActionListener(e->new SwingWorker<java.io.File,Void>(){protected java.io.File doInBackground()throws Exception{return services.EmployeeDocumentService.downloadAuthenticatedDocument(row.documentUrl());}protected void done(){try{Desktop.getDesktop().open(get());}catch(Exception ex){JOptionPane.showMessageDialog(panel,error(ex));}}}.execute());files.add(legacy);}
        panel.add(files,BorderLayout.SOUTH);JOptionPane.showMessageDialog(this,panel,"Application details",JOptionPane.PLAIN_MESSAGE);
    }
    private void openDocument(UUID application,UUID attachment,JButton button){button.setEnabled(false);new SwingWorker<java.io.File,Void>(){
        protected java.io.File doInBackground()throws Exception{JsonObject body=new JsonObject();body.addProperty("attachmentId",attachment.toString());JsonObject doc=LanApiClient.employeeApplicationRequest("DOCUMENT",application,body);String type=doc.get("contentType").getAsString();java.nio.file.Path directory=data.EnvironmentProfile.active().directory().resolve("application-document-cache");java.nio.file.Files.createDirectories(directory);utils.SecureFilePermissions.restrictDirectoryToOwner(directory);java.nio.file.Path path=directory.resolve(attachment+(type.equals("application/pdf")?".pdf":type.equals("image/png")?".png":".jpg"));java.nio.file.Files.write(path,java.util.Base64.getDecoder().decode(doc.get("base64").getAsString()));utils.SecureFilePermissions.restrictFileToOwner(path);return path.toFile();}
        protected void done(){button.setEnabled(true);try{Desktop.getDesktop().open(get());}catch(Exception e){JOptionPane.showMessageDialog(EmployeeRegistrationPanel.this,error(e));}}
    }.execute();}
    static String display(String status){return switch(status){case "PENDING"->"Submitted";case "UNDER_REVIEW"->"Under review";case "INTERVIEW"->"Interview";case "INFORMATION_REQUESTED"->"Information requested";case "APPROVED"->"Hired";case "APPROVING"->"Hiring in progress";case "REJECTED"->"Not selected";case "WITHDRAWN"->"Withdrawn";default->status;};}
    static String error(Exception e){Throwable cause=e.getCause();return cause==null?e.getMessage():cause.getMessage();}
}
