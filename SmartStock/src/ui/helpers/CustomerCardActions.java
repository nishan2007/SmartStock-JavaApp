package ui.helpers;

import services.CustomerCardService;
import services.LanApiClient;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.time.LocalDate;

public final class CustomerCardActions {
    private CustomerCardActions() { }

    public enum Action { PREVIEW, PRINT, PDF }

    public static void run(Component parent, LanApiClient.CustomerAccountRecord account, Action action) {
        if (account == null) { JOptionPane.showMessageDialog(parent,"Select a customer account first."); return; }
        if (text(account.accountNumber()).isBlank()) { JOptionPane.showMessageDialog(parent,"The selected account needs an account number before a card can be created."); return; }
        try {
            List<CustomerCardService.Template> templates=CustomerCardService.load();
            String[] choices=new String[templates.size()];
            for(int i=0;i<choices.length;i++) choices[i]=(i+1)+" — "+templates.get(i).name()+(templates.get(i).configured()?"":" (blank)");
            JComboBox<String> picker=new JComboBox<>(choices);
            int assignedSlot=account.customerCardTemplateSlot()>=1&&account.customerCardTemplateSlot()<=5?account.customerCardTemplateSlot():4;
            picker.setSelectedIndex(assignedSlot-1);
            int result=JOptionPane.showConfirmDialog(parent,picker,"Choose Customer Card Template",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
            if(result!=JOptionPane.OK_OPTION)return;
            int slot=picker.getSelectedIndex()+1;
            LocalDate expiry=account.customerCardExpiresOn();
            if(action!=Action.PREVIEW){JTextField expiryField=new JTextField((expiry==null?LocalDate.now().plusYears(2):expiry).toString(),12);
                int expiryResult=JOptionPane.showConfirmDialog(parent,new Object[]{"Card expiry date (YYYY-MM-DD):",expiryField},"Customer Card Expiry",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
                if(expiryResult!=JOptionPane.OK_OPTION)return;try{expiry=LocalDate.parse(expiryField.getText().trim());}catch(Exception ex){throw new IllegalArgumentException("Enter the expiry date as YYYY-MM-DD.");}
                if(account.customerCardExpiresOn()==null||!account.customerCardExpiresOn().equals(expiry)){LanApiClient.issueCustomerCard(account.customerId(),expiry,account.customerCardExpiresOn()!=null);}
            }else if(expiry==null)expiry=LocalDate.now().plusYears(2);
            CustomerCardService.CardData data=new CustomerCardService.CardData(account.customerId(),text(account.name()),
                    text(account.customerTypeName()),text(account.accountNumber()),text(account.phone()),text(account.email()),account.customerSince(),text(account.customerPhotoUrl()),expiry,slot);
            CustomerCardService.Template template=templates.get(slot-1);
            if(action==Action.PREVIEW){CustomerCardService.preview(parent,data,template);return;}
            if(action==Action.PRINT){CustomerCardService.print(data,template);audit(parent,account.customerId(),slot,"PRINT");JOptionPane.showMessageDialog(parent,"Customer card sent to the configured card printer.");return;}
            JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Save Customer Card PDF");
            chooser.setFileFilter(new FileNameExtensionFilter("PDF files","pdf"));
            chooser.setSelectedFile(new File(safeName(account.accountNumber())+"-customer-card.pdf"));
            if(chooser.showSaveDialog(parent)!=JFileChooser.APPROVE_OPTION)return;
            File output=chooser.getSelectedFile();if(!output.getName().toLowerCase().endsWith(".pdf"))output=new File(output.getParentFile(),output.getName()+".pdf");
            CustomerCardService.savePdf(output.toPath(),data,template);
            audit(parent,account.customerId(),slot,"PDF_EXPORT");
            JOptionPane.showMessageDialog(parent,"Customer card PDF saved to:\n"+output.getAbsolutePath());
        } catch(Exception ex) { JOptionPane.showMessageDialog(parent,"Customer card could not be created: "+ex.getMessage(),"Customer Card",JOptionPane.ERROR_MESSAGE); }
    }

    private static String text(String value){return value==null?"":value.trim();}
    private static String safeName(String value){String v=text(value).replaceAll("[^A-Za-z0-9._-]+","-");return v.isBlank()?"customer":v;}
    private static void audit(Component parent,int customerId,int slot,String action){try{LanApiClient.auditCustomerCardOutput(customerId,slot,action);}catch(Exception ex){JOptionPane.showMessageDialog(parent,"The card output succeeded, but its audit event could not be recorded: "+ex.getMessage(),"Audit Warning",JOptionPane.WARNING_MESSAGE);}}
}
