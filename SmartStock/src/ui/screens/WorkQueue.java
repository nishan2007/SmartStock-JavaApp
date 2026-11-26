package ui.screens;

import managers.SessionManager;
import ui.components.AppMenuBar;
import ui.design.DeckersLogoManager;
import ui.design.DeckersPalette;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Combined custom-order and sales-task production workspace. */
public final class WorkQueue extends JFrame {
    public WorkQueue(){
        super("SmartStock – Work Queue");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(1672,941);setMinimumSize(new Dimension(1120,720));setLocationRelativeTo(null);
        setJMenuBar(AppMenuBar.create(this,"WorkQueue"));
        OrdersWorkQueuePanel queue=new OrdersWorkQueuePanel(this,row->{
            if("SALES_TASK".equals(row.kind()))managers.NavigationManager.openSalesTasks(this,row.id());
            else managers.NavigationManager.openOrders(this,row.id(),row.number());
        });
        JPanel root=new JPanel(new BorderLayout());root.add(header(),BorderLayout.NORTH);root.add(queue);setContentPane(root);
        queue.refresh();
    }
    static JPanel createHeader(String store,String staff,LocalDate date,Icon logo){
        JPanel header=new JPanel(new BorderLayout(24,0));header.setBackground(DeckersPalette.dark()?DeckersPalette.background():new Color(237,247,255));
        header.putClientProperty("SmartStock.preserveThemeColors",true);
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,new Color(207,225,240)),new EmptyBorder(8,40,8,30)));header.setPreferredSize(new Dimension(1000,68));
        JLabel brand=new JLabel(logo);brand.setPreferredSize(new Dimension(168,50));header.putClientProperty("logo",brand);
        JPanel left=new JPanel(new BorderLayout(24,0));left.setOpaque(false);left.add(brand,BorderLayout.WEST);
        JPanel titles=new JPanel(new GridLayout(2,1));titles.setOpaque(false);titles.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,1,0,0,new Color(194,216,238)),new EmptyBorder(0,24,0,0)));
        titles.add(OrdersWorkQueuePanel.label("SmartStock",27,true));
        JPanel subtitle=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));subtitle.setOpaque(false);subtitle.add(OrdersWorkQueuePanel.label("Work Queue",18,true));subtitle.add(OrdersWorkQueuePanel.label("   |   Custom orders & sales tasks",16,false));titles.add(subtitle);left.add(titles);header.add(left);
        JPanel metadata=new JPanel(new GridLayout(1,3,25,0));metadata.setOpaque(false);metadata.add(meta("Store",store,"pin"));metadata.add(meta("Staff",staff,"person"));JPanel calendar=meta("","","calendar");metadata.add(calendar);header.putClientProperty("calendar",calendar);updateHeaderDate(header,date);header.add(metadata,BorderLayout.EAST);return header;
    }
    static void updateHeaderDate(JPanel header,LocalDate date){
        JPanel calendar=(JPanel)header.getClientProperty("calendar");
        JPanel lines=(JPanel)((BorderLayout)calendar.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        ((JLabel)lines.getComponent(0)).setText(date.format(DateTimeFormatter.ofPattern("EEEE")));
        ((JLabel)lines.getComponent(1)).setText(date.format(DateTimeFormatter.ofPattern("d MMM yyyy")));
    }
    private JPanel header(){
        Icon fallback=null;var resource=WorkQueue.class.getResource("/Images/Deckers.png");if(resource!=null)fallback=new ImageIcon(DeckersLogoManager.scaleToFit(new ImageIcon(resource).getImage(),168,50));
        JPanel header=createHeader(Objects.toString(SessionManager.getCurrentLocationName(),"—"),Objects.toString(SessionManager.getCurrentUserDisplayName(),"—"),LocalDate.now(StoreTimeZoneHelper.getStoreZone()),fallback);
        UiTaskRunner.submit(this,"work-queue-branding",()->{var settings=managers.CompanyCustomizationManager.loadReceiptSettings();return managers.CompanyCustomizationManager.loadCompanyLogo(settings);},image->{if(image!=null)((JLabel)header.getClientProperty("logo")).setIcon(new ImageIcon(DeckersLogoManager.scaleToFit(image,168,50)));},error->{});return header;
    }
    private static JPanel meta(String name,String value,String icon){JPanel panel=new JPanel(new BorderLayout(12,0));panel.setOpaque(false);panel.setBorder(new EmptyBorder(2,14,2,8));panel.add(new JLabel(SalesTaskVisuals.icon(icon,OrdersWorkQueuePanel.ink(),20)),BorderLayout.WEST);JPanel lines=new JPanel(new GridLayout(2,1));lines.setOpaque(false);lines.add(OrdersWorkQueuePanel.label(name,14,false));lines.add(OrdersWorkQueuePanel.label(value,18,true));panel.add(lines);return panel;}
}
