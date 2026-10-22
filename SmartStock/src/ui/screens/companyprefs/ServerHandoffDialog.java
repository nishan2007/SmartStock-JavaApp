package ui.screens.companyprefs;

import com.google.gson.*;
import services.ServerHandoffDiagnostics;
import ui.design.*;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.file.*;
import java.util.concurrent.Callable;

/** Explicit local opt-in; closing the dialog revokes its temporary connection. */
public final class ServerHandoffDialog extends JDialog {
    private final JTextArea output=new JTextArea(18,65);
    private final JTextField link=new JTextField();
    private final JPanel actions=new JPanel(new GridLayout(2,3,8,8));
    private JsonObject baseline;
    public ServerHandoffDialog(Window owner) {
        super(owner,"Server handoff verification",ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel body=new JPanel(new BorderLayout(8,12));body.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        body.setBackground(Color.WHITE);
        body.add(new JLabel("<html>Capture a baseline after writes stop. On the replacement, load it and share a fresh report.<br>Sharing allows read-only internet access for 30 minutes. Closing this window revokes access.</html>"),BorderLayout.NORTH);
        output.setEditable(false);output.setLineWrap(true);output.setWrapStyleWord(true);
        output.setText("Checks include open drawer sessions, cash-activity content, register credentials, permissions and server identity. Keep the drawer open and stop register activity for the final baseline. A successful comparison still requires authenticated reconnection and drawer verification on each register.");
        body.add(new JScrollPane(output),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(8,8)); link.setEditable(false);
        footer.add(link,BorderLayout.NORTH); footer.add(actions,BorderLayout.CENTER);body.add(footer,BorderLayout.SOUTH);
        button("Save old-server baseline",()->{
            JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File("server-handoff-baseline.json"));
            if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
            Path path=chooser.getSelectedFile().toPath();
            run(()->{JsonObject report=ServerHandoffDiagnostics.capture();ServerHandoffDiagnostics.save(path,report);return "Baseline saved privately. Keep it with the migration backup.";});
        });
        button("Load baseline",()->{
            JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
            try {
                Path path=chooser.getSelectedFile().toPath();if(Files.size(path)>2*1024*1024)throw new IllegalArgumentException("Baseline is too large.");
                JsonObject selected=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if(!selected.has("format")||selected.get("format").getAsInt()!=1)throw new IllegalArgumentException("Unsupported baseline.");
                baseline=selected;output.setText("Baseline loaded. Generate a fresh comparison on this server.");
            }catch(Exception e){output.setText("Cannot load this baseline.");}
        });
        button("Share diagnostics (30 min)",()->{
            JsonObject selected=baseline;
            run(()->{JsonObject report=ServerHandoffDiagnostics.capture();if(selected!=null)report.add("comparison",ServerHandoffDiagnostics.compare(selected,report));
                String url=ServerHandoffDiagnostics.share(report);SwingUtilities.invokeLater(()->link.setText(url));
                return new GsonBuilder().setPrettyPrinting().create().toJson(report);});
        });
        button("Copy link",()->{if(!link.getText().isBlank())Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(link.getText()),null);});
        button("Revoke",()->{ServerHandoffDiagnostics.revoke();link.setText("");output.setText("Internet diagnostics revoked.");});
        addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){ServerHandoffDiagnostics.revoke();}});
        setContentPane(body);setSize(940,620);setMinimumSize(new Dimension(760,460));setLocationRelativeTo(owner);
    }
    private void button(String title,Runnable action){JButton b=new JButton(title);DeckersSwing.styleUtilityButton(b,DeckersPalette.PURPLE);b.addActionListener(e->action.run());actions.add(b);}
    private void run(Callable<String> task) {
        for(Component c:actions.getComponents())c.setEnabled(false);output.setText("Checking local data. Large photo/model folders may take several minutes…");
        new SwingWorker<String,Void>() {
            protected String doInBackground() throws Exception{return task.call();}
            protected void done(){
                for(Component c:actions.getComponents())c.setEnabled(true);
                try {String result=get();if(!isDisplayable()){ServerHandoffDiagnostics.revoke();return;}output.setText(result);}
                catch(Exception e){output.setText("Diagnostics failed. Check database access, administrator permissions and internet connection. No complete handoff is claimed.");}
            }
        }.execute();
    }
}
