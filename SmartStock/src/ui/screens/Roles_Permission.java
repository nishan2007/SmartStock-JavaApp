package ui.screens;

import managers.PermissionManager;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.WindowHelper;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Role administration backed exclusively by the authenticated SmartStock LAN service. */
public class Roles_Permission extends JFrame {
    private final DefaultListModel<RoleItem> roleModel = new DefaultListModel<>();
    private final JList<RoleItem> roleList = new JList<>(roleModel);
    private final JPanel desktopPanel = permissionColumn();
    private final JPanel mobilePanel = permissionColumn();
    private final Map<String,JCheckBox> desktopChecks = new LinkedHashMap<>();
    private final Map<String,JCheckBox> mobileChecks = new LinkedHashMap<>();
    private JTabbedPane permissionTabs;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();
    private LanApiClient.RoleAdminState state;

    public Roles_Permission() {
        setTitle("Role & Permission Management");
        setSize(1000,700);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10,10));
        setJMenuBar(AppMenuBar.create(this,"Roles_Permissions"));

        roleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roleList.setCellRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?>l,Object v,int i,boolean s,boolean f){return super.getListCellRendererComponent(l,v instanceof RoleItem r?format(r.name):v,i,s,f);}});
        roleList.addListSelectionListener(e->{if(!e.getValueIsAdjusting())loadSelection();});

        JPanel left=new JPanel(new BorderLayout(6,6));
        left.setBorder(BorderFactory.createTitledBorder("Roles"));
        left.add(new JScrollPane(roleList),BorderLayout.CENTER);
        JButton addRole=new JButton("Add Role");
        addRole.addActionListener(e->addRole());
        left.add(addRole,BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(230,0));

        permissionTabs=new JTabbedPane();
        permissionTabs.addTab("Desktop",new JScrollPane(desktopPanel));
        permissionTabs.addTab("Mobile App",new JScrollPane(mobilePanel));
        JButton save=new JButton("Save Permissions");
        save.addActionListener(e->savePermissions());
        JPanel right=new JPanel(new BorderLayout(6,6));
        right.setBorder(BorderFactory.createEmptyBorder(8,0,8,8));
        right.add(permissionTabs,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(save);right.add(actions,BorderLayout.SOUTH);

        add(left,BorderLayout.WEST);add(right,BorderLayout.CENTER);add(loadingState,BorderLayout.SOUTH);
        WindowHelper.configurePosWindow(this);
        loadState(null);
    }

    public static void openWindow() {
        if (WindowHelper.focusIfAlreadyOpen(Roles_Permission.class)) return;
        SwingUtilities.invokeLater(()->new Roles_Permission().setVisible(true));
    }

    private void loadState(Integer selectRoleId) {
        CachedUiLoader.load(this,"roles.admin.state",LanApiClient.RoleAdminState.class,
                SessionDataCache.REFERENCE_TTL,loadingState,LanApiClient::loadRoleAdminState,loaded->{
            state=loaded;
            renderDefinitions(desktopPanel,desktopChecks,state.permissions());
            renderDefinitions(mobilePanel,mobileChecks,state.mobilePermissions());
            permissionTabs.setEnabledAt(1,state.mobileAvailable());
            roleModel.clear();
            if(state.roles()!=null)for(LanApiClient.RoleRecord r:state.roles())roleModel.addElement(new RoleItem(r.roleId(),r.name()));
            int selected=0;if(selectRoleId!=null)for(int i=0;i<roleModel.size();i++)if(roleModel.get(i).id==selectRoleId){selected=i;break;}
            if(!roleModel.isEmpty())roleList.setSelectedIndex(selected);
        });
    }

    private void renderDefinitions(JPanel target,Map<String,JCheckBox>checks,List<LanApiClient.PermissionRecord>definitions) {
        target.removeAll();checks.clear();
        Map<String,Map<String,List<LanApiClient.PermissionRecord>>>groups=new LinkedHashMap<>();
        if(definitions!=null)for(var d:definitions)groups.computeIfAbsent(text(d.group(),"General"),k->new LinkedHashMap<>()).computeIfAbsent(text(d.subgroup(),"General"),k->new ArrayList<>()).add(d);
        for(var group:groups.entrySet()){
            JPanel groupPanel=new JPanel();groupPanel.setLayout(new BoxLayout(groupPanel,BoxLayout.Y_AXIS));groupPanel.setBorder(BorderFactory.createTitledBorder(format(group.getKey())));groupPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            for(var subgroup:group.getValue().entrySet()){
                JLabel label=new JLabel(format(subgroup.getKey()));label.setFont(label.getFont().deriveFont(Font.BOLD));label.setBorder(BorderFactory.createEmptyBorder(5,8,2,0));groupPanel.add(label);
                for(var d:subgroup.getValue()){JCheckBox box=new JCheckBox(text(d.label(),format(d.key())));box.setToolTipText(d.key()+((d.description()==null||d.description().isBlank())?"":" - "+d.description()));box.setAlignmentX(Component.LEFT_ALIGNMENT);box.setBorder(BorderFactory.createEmptyBorder(1,24,1,0));checks.put(d.key().toUpperCase(),box);groupPanel.add(box);}
            }
            target.add(groupPanel);target.add(Box.createVerticalStrut(6));
        }
        if(groups.isEmpty())target.add(new JLabel("No permissions are configured for this area."));
        target.revalidate();target.repaint();
    }

    private void loadSelection() {
        RoleItem role=roleList.getSelectedValue();if(role==null)return;
        desktopChecks.values().forEach(b->b.setSelected(false));mobileChecks.values().forEach(b->b.setSelected(false));
        String key="roles.selection."+role.id;
        CachedUiLoader.load(this,"roles.selection",key,LanApiClient.RolePermissionSelection.class,SessionDataCache.SCREEN_TTL,
                loadingState,()->LanApiClient.loadRolePermissionSelection(role.id),selected->{
                    select(desktopChecks,selected.permissionKeys());select(mobileChecks,selected.mobilePermissionKeys());
                });
    }

    private void savePermissions() {
        RoleItem role=roleList.getSelectedValue();if(role==null)return;
        try {LanApiClient.saveRolePermissions(role.id,selected(desktopChecks),selected(mobileChecks),UUID.randomUUID().toString());SessionDataCache.invalidate("roles.");if(role.name!=null&&role.name.equalsIgnoreCase(PermissionManager.getCurrentRole()))PermissionManager.refreshOpenWindows();JOptionPane.showMessageDialog(this,"Permissions updated.");}
        catch(Exception ex){showError("Permissions could not be saved",ex);}
    }

    private void addRole() {
        String name=JOptionPane.showInputDialog(this,"Enter new role name:");if(name==null||name.isBlank())return;
        try {var added=LanApiClient.addRole(name,UUID.randomUUID().toString());SessionDataCache.invalidate("roles.");loadState(added.roleId());}
        catch(Exception ex){showError("The role could not be added",ex);}
    }

    private static void select(Map<String,JCheckBox>checks,List<String>keys){if(keys!=null)for(String key:keys){JCheckBox b=checks.get(key.toUpperCase());if(b!=null)b.setSelected(true);}}
    private static Set<String>selected(Map<String,JCheckBox>checks){Set<String>x=new LinkedHashSet<>();checks.forEach((k,v)->{if(v.isSelected())x.add(k);});return x;}
    private static JPanel permissionColumn(){JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));return p;}
    private void showError(String message,Exception ex){JOptionPane.showMessageDialog(this,message+".\n\n"+ex.getMessage(),"SmartStock Server",JOptionPane.ERROR_MESSAGE);}
    private static String text(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    private static String format(String value){if(value==null)return "";StringBuilder b=new StringBuilder();for(String word:value.trim().replace('_',' ').split("\\s+")){if(!b.isEmpty())b.append(' ');b.append(Character.toUpperCase(word.charAt(0)));if(word.length()>1)b.append(word.substring(1).toLowerCase());}return b.toString();}
    private record RoleItem(int id,String name){}
}
