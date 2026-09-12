package ui.screens.companyprefs;

import data.EnvironmentProfile;
import services.LanApiClient;
import ui.helpers.UiTaskRunner;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

/** Company Preferences controls for the public employment application portal. */
public final class EmploymentPortalPanel extends JPanel {
    private final JTextField origin = new JTextField();
    private final JLabel status = new JLabel("Loading portal status...");
    private final JButton startStop = new JButton("Start portal");
    private final JButton refresh = new JButton("Refresh");
    private boolean running;

    public EmploymentPortalPanel() {
        super(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(18, 18, 18, 18));
        JLabel title = new JLabel("Employment Application Portal");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel help = new JLabel("Configure the public HTTPS link applicants will use. Company branding updates automatically from Company Identity.");
        JPanel heading = new JPanel(new BorderLayout(4, 4)); heading.add(title, BorderLayout.NORTH); heading.add(help, BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints(); g.insets = new Insets(8, 4, 8, 4); g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        g.gridx=0; g.gridy=0; g.weightx=0; form.add(new JLabel("Public HTTPS URL"),g);
        g.gridx=1; g.weightx=1; origin.setToolTipText("Example: https://careers.example.com"); form.add(origin,g);
        g.gridx=1; g.gridy=1; g.weightx=1; form.add(new JLabel("Use a permanent HTTPS reverse proxy or named tunnel to this server's local portal listener."),g);
        JButton emailSetup = new JButton("Configure verification emails");
        g.gridy=2; form.add(emailSetup,g);
        emailSetup.addActionListener(e -> configureEmail());
        add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton save = new JButton("Save settings"); JButton copy = new JButton("Copy application link");
        actions.add(save); actions.add(startStop); actions.add(copy); actions.add(refresh); actions.add(status); add(actions, BorderLayout.SOUTH);
        save.addActionListener(e -> save()); refresh.addActionListener(e -> load());
        startStop.addActionListener(e -> toggle());
        copy.addActionListener(e -> { if(!origin.getText().isBlank()) Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(origin.getText().replaceAll("/$", "")+"/register"), null); });
        startStop.setEnabled(false);
    }

    @Override public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(() -> {
            if (isDisplayable() && SwingUtilities.getWindowAncestor(this) != null) load();
        });
    }

    private void load() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner == null || !owner.isDisplayable()) return;
        startStop.setEnabled(false);
        status.setText("Loading portal status...");
        try { Properties p = new Properties(); var f=EnvironmentProfile.active().file("applications.properties"); if(Files.exists(f)) try(var in=Files.newInputStream(f)){p.load(in);} origin.setText(p.getProperty("public.origin", "")); } catch(Exception ignored) {}
        UiTaskRunner.submit(owner,"employment-portal.status",()->LanApiClient.employeeRegistrationAction("STATUS",null,null,null,false),r->{running=r.has("running")&&r.get("running").getAsBoolean();startStop.setText(running?"Stop portal":"Start portal");startStop.setEnabled(true);status.setText(running?"Running: "+r.get("url").getAsString():"Stopped");},e->status.setText("Unable to read portal status. Check the server connection, then Refresh."));
    }
    private void configureEmail() {
        Window owner=SwingUtilities.getWindowAncestor(this);
        UiTaskRunner.submit(owner,"employment-portal.email-status",()->LanApiClient.employeeRegistrationAction("EMAIL_STATUS",null,null,null,false),r->{
            JPanel panel=new JPanel(new GridLayout(0,1,6,6));
            var sender=r.getAsJsonObject("sender");
            panel.add(new JLabel("Gmail sender: "+sender.get("senderEmail").getAsString()));
            panel.add(new JLabel(sender.get("message").getAsString()));
            JTextField url=new JTextField(origin.getText().replaceAll("/$","")+services.SupabaseAuthEmailHook.PATH);url.setEditable(false);
            panel.add(new JLabel("Supabase Send Email hook URL (copy this):"));panel.add(url);
            panel.add(new JLabel("Paste the hook signing secret below. Enable the hook only after the server is updated."));
            JPasswordField secret=new JPasswordField();panel.add(secret);
            panel.add(new JLabel(r.get("configured").getAsBoolean()?"A signing secret is already configured.":"No signing secret configured."));
            if(JOptionPane.showConfirmDialog(this,panel,"Verification and recovery emails",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            char[] chars=secret.getPassword();String value=new String(chars);java.util.Arrays.fill(chars,'\0');secret.setText("");
            if(value.isBlank())return;
            UiTaskRunner.submit(owner,"employment-portal.email-configure",()->LanApiClient.employeeRegistrationAction("EMAIL_CONFIGURE",null,null,value,false),saved->status.setText("Email signing secret saved. Verify delivery before enabling the Supabase hook."),ex->JOptionPane.showMessageDialog(this,"Could not configure authentication email."));
        },ex->JOptionPane.showMessageDialog(this,"Could not check Gmail. Check the server connection and company permissions."));
    }
    private void save() {
        String value=origin.getText().trim(); if(!value.matches("https://[^/\\s]+")){JOptionPane.showMessageDialog(this,"Enter a permanent HTTPS URL, for example https://careers.example.com.","Invalid URL",JOptionPane.ERROR_MESSAGE);return;}
        try { var profile=EnvironmentProfile.active(); Files.createDirectories(profile.directory()); Properties p=new Properties(); p.setProperty("public.origin",value); try(OutputStream out=Files.newOutputStream(profile.file("applications.properties"))){p.store(out,"SmartStock employment portal settings");} status.setText("Settings saved"); } catch(Exception e){JOptionPane.showMessageDialog(this,"Could not save portal settings: "+e.getMessage(),"Save failed",JOptionPane.ERROR_MESSAGE);}
    }
    private void toggle() { save(); UiTaskRunner.submit((Window)SwingUtilities.getWindowAncestor(this),"employment-portal.toggle",()->LanApiClient.employeeRegistrationAction(running?"STOP":"START",null,null,null,false),r->load(),e->JOptionPane.showMessageDialog(this,"Could not change portal state: "+e.getMessage(),"Portal",JOptionPane.ERROR_MESSAGE)); }
}
