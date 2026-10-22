package ui.screens.companyprefs;

import services.LanApiClient;
import services.LoyaltyPolicy;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Shared policy editor; server enforces administrator access and optimistic versioning. */
public final class LoyaltyPanel extends JPanel {
    private final JCheckBox enabled=new JCheckBox("Enable customer loyalty");
    private final JTextField authority=new JTextField(12),spend=new JTextField("100.00",12),award=new JTextField("1",12),value=new JTextField("1.00",12),minimum=new JTextField("100",12),cap=new JTextField("20",12);
    private final JLabel example=new JLabel("Loading loyalty settings…");
    private final JButton save=new JButton("Save loyalty settings"),refresh=new JButton("Refresh");
    private long version;
    public LoyaltyPanel(){this(true);}
    LoyaltyPanel(boolean autoLoad){
        super(new BorderLayout(12,16));setOpaque(false);
        var fields=new JPanel(new GridBagLayout());fields.setOpaque(false);var g=new GridBagConstraints();g.insets=new Insets(6,6,6,6);g.anchor=GridBagConstraints.WEST;g.fill=GridBagConstraints.HORIZONTAL;g.weightx=1;g.gridx=0;g.gridy=0;g.gridwidth=2;fields.add(enabled,g);g.gridwidth=1;
        row(fields,g,"Authority store location ID",authority);row(fields,g,"Eligible G$ spending per award",spend);row(fields,g,"Points awarded",award);row(fields,g,"G$ value per newly earned point",value);row(fields,g,"Minimum points per redemption",minimum);row(fields,g,"Maximum eligible bill covered (%)",cap);
        g.gridy++;g.gridx=0;g.gridwidth=2;fields.add(example,g);
        var note=new JTextArea("Mark delivery and gift-card products as ineligible using Product eligibility / exclusions before enabling loyalty. Existing points retain the value at which they were earned. Customer discounts can be combined with loyalty. Points do not expire. Credit sales earn when paid; custom orders earn when paid and completed.\n\nUse the numeric location ID shown in Locations. To join an existing authority, enter its ID and save; its shared rules are adopted. Server connectivity and peer trust must be configured by the administrator on each store server. Changing the authority is blocked once loyalty history exists.");note.setEditable(false);note.setLineWrap(true);note.setWrapStyleWord(true);note.setOpaque(false);note.setFont(UIManager.getFont("Label.font"));
        add(fields,BorderLayout.NORTH);add(note,BorderLayout.CENTER);var buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));buttons.setOpaque(false);buttons.add(save);buttons.add(refresh);JButton exclusions=new JButton("Product eligibility / exclusions");exclusions.addActionListener(e->LoyaltyEligibilityDialog.open(this));var actions=new JPanel();actions.setOpaque(false);actions.setLayout(new BoxLayout(actions,BoxLayout.Y_AXIS));actions.add(buttons);var eligibilityActions=new JPanel(new FlowLayout(FlowLayout.LEFT));eligibilityActions.setOpaque(false);eligibilityActions.add(exclusions);actions.add(eligibilityActions);add(actions,BorderLayout.SOUTH);
        var listener=new DocumentListener(){public void insertUpdate(DocumentEvent e){preview();}public void removeUpdate(DocumentEvent e){preview();}public void changedUpdate(DocumentEvent e){preview();}};
        for(var f:new JTextField[]{spend,award,value,minimum,cap})f.getDocument().addDocumentListener(listener);
        save.addActionListener(e->save());refresh.addActionListener(e->load());if(autoLoad)load();else show(LoyaltyPolicy.Settings.defaults());
    }
    private void row(JPanel p,GridBagConstraints g,String title,JComponent field){g.gridy++;g.gridx=0;p.add(new JLabel(title),g);g.gridx=1;p.add(field,g);}
    private LoyaltyPolicy.Settings input(){String id=authority.getText().trim();return new LoyaltyPolicy.Settings(version,enabled.isSelected(),id.isEmpty()?null:Integer.valueOf(id),new BigDecimal(spend.getText().trim()),Long.parseLong(award.getText().trim()),new BigDecimal(value.getText().trim()),Long.parseLong(minimum.getText().trim()),new BigDecimal(cap.getText().trim()));}
    private void preview(){try{var s=input();example.setText("Reward rate: "+s.rewardPercent().stripTrailingZeros().toPlainString()+"% · G$10,000 earns "+new BigDecimal("10000").multiply(BigDecimal.valueOf(s.pointsPerAward())).divide(s.spendPerAward(),0,java.math.RoundingMode.DOWN).toPlainString()+" points");}catch(Exception e){example.setText("Enter valid positive amounts and whole points.");}}
    private void show(LoyaltyPolicy.Settings s){version=s.version();enabled.setSelected(s.enabled());authority.setText(s.authorityLocationId()==null?"":s.authorityLocationId().toString());spend.setText(s.spendPerAward().toPlainString());award.setText(Long.toString(s.pointsPerAward()));value.setText(s.pointValue().toPlainString());minimum.setText(Long.toString(s.minimumRedemption()));cap.setText(s.redemptionCapPercent().toPlainString());preview();}
    private void load(){busy(true);new SwingWorker<LoyaltyPolicy.Settings,Void>(){protected LoyaltyPolicy.Settings doInBackground()throws Exception{return LanApiClient.loadLoyaltySettings();}protected void done(){try{show(get());}catch(Exception e){error(e);}finally{busy(false);}}}.execute();}
    private void save(){final LoyaltyPolicy.Settings s;try{s=input();}catch(Exception e){error(e);return;}busy(true);String key=UUID.randomUUID().toString();new SwingWorker<LoyaltyPolicy.Settings,Void>(){protected LoyaltyPolicy.Settings doInBackground()throws Exception{return LanApiClient.saveLoyaltySettings(s,key);}protected void done(){try{show(get());JOptionPane.showMessageDialog(LoyaltyPanel.this,"Loyalty settings saved.");}catch(Exception e){error(e);}finally{busy(false);}}}.execute();}
    private void busy(boolean b){save.setEnabled(!b);refresh.setEnabled(!b);}
    private void error(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();JOptionPane.showMessageDialog(this,cause.getMessage(),"Loyalty",JOptionPane.ERROR_MESSAGE);}
}
