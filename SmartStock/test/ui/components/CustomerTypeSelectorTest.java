package ui.components;
import org.junit.jupiter.api.Test;
import services.LanApiClient;
import javax.swing.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class CustomerTypeSelectorTest {
 @Test void dropdownSelectsExistingTypesAndPreservesSavedAccountType()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   var selector=new CustomerTypeSelector();var f=CustomerTypeSelector.class.getDeclaredField("customerTypeBox");f.setAccessible(true);JComboBox<?> box=(JComboBox<?>)f.get(selector);
   assertFalse(box.isEditable());
   var apply=CustomerTypeSelector.class.getDeclaredMethod("applyCustomerTypes",List.class,String.class);apply.setAccessible(true);
   apply.invoke(selector,List.of(new LanApiClient.CustomerTypeRecord(1,"Retail","",true,false,1),new LanApiClient.CustomerTypeRecord(2,"School","",true,false,1)),"");
   assertEquals(3,box.getItemCount());box.setSelectedIndex(2);assertEquals(2,selector.getSelectedCustomerTypeId());assertEquals("School",selector.getSelectedCustomerTypeName());
   selector.setSelectedCustomerType(8,"Existing inactive type");assertEquals(8,selector.getSelectedCustomerTypeId());
   selector.clearSelection();assertNull(selector.getSelectedCustomerTypeId());assertEquals("",selector.getSelectedCustomerTypeName());
  }catch(Exception ex){throw new RuntimeException(ex);}});
 }
}
