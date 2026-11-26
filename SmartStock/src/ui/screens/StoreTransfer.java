package ui.screens;

import managers.PermissionManager;
import managers.SessionManager;
import ui.components.AppMenuBar;
import ui.design.DeckersLogoManager;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.ThemeManager;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;
import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Native transfer desk. Register-facing reads and writes use the authenticated HTTPS LAN API. */
public class StoreTransfer extends JFrame {
    private final StoreTransferWorkspace workspace;
    private final StoreTransferController controller;
    public StoreTransfer(){
        super("Store Transfer");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this,"StoreTransfer"));
        var zone=StoreTimeZoneHelper.getStoreZone();
        workspace=new StoreTransferWorkspace("SmartStock",null,Objects.toString(SessionManager.getCurrentLocationName(),"No store selected"),Objects.toString(SessionManager.getCurrentUserDisplayName(),"—"),zone,LocalDateTime.now(zone));
        controller=new StoreTransferController(workspace,new StoreTransferController.LanGateway(),new StoreTransferController.Tasks(){
            public <T> void run(String key,Callable<T> task,Consumer<T> success,Consumer<Throwable> failure){UiTaskRunner.submit(StoreTransfer.this,key,task,success,failure);}
        },new StoreTransferController.Dialogs(){
            public boolean authorize(String action){return PermissionManager.requirePermission("STORE_TRANSFER",StoreTransfer.this,action);}
            public boolean confirm(String title,String message){return JOptionPane.showConfirmDialog(StoreTransfer.this,message,title,JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
            public void information(String message){JOptionPane.showMessageDialog(StoreTransfer.this,message);}
            public void close(){StoreTransfer.this.dispose();}
        });
        setContentPane(workspace);
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){controller.back();}@Override public void windowClosed(WindowEvent e){controller.closed();}});
        UiTaskRunner.submit(this,"store-transfer.branding",()->{
            var settings=managers.CompanyCustomizationManager.loadReceiptSettings();
            var image=managers.CompanyCustomizationManager.loadCompanyLogo(settings);
            return new Branding(settings.companyName(),image==null?null:new ImageIcon(DeckersLogoManager.scaleToFit(image,190,64)));
        },branding->{
            ((JLabel)workspace.brandHeader.getClientProperty("company")).setText(branding.name());
            JLabel logo=(JLabel)workspace.brandHeader.getClientProperty("logo");logo.setIcon(branding.logo());logo.setVisible(branding.logo()!=null);
        },error->workspace.message.setText("Company branding could not be loaded."));
        Integer draftUser=SessionManager.getCurrentUserId(),draftStore=SessionManager.getCurrentLocationId();
        if(draftUser!=null&&draftUser>0&&draftStore!=null&&draftStore>0)
            controller.drafts(new TransferDraftStore(data.EnvironmentProfile.active().directory().resolve("transfer-drafts").resolve("store-"+draftStore).resolve("user-"+draftUser)));
        controller.refresh();
        ThemeManager.applyToWindow(this);WindowHelper.configurePosWindow(this);
    }
    private record Branding(String name,Icon logo){}
}
