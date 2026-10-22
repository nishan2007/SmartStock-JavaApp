package services;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CustomItemCategoryTest {
 @Test void eachSelectionClearsAllOtherFlags(){
  JsonObject item=new JsonObject();
  for(var category:CustomItemCategory.values()){
   category.apply(item);assertEquals(category,CustomItemCategory.from(item));
   assertEquals(category==CustomItemCategory.NONE?0:1,item.entrySet().stream().filter(e->e.getValue().getAsBoolean()).count());
  }
 }
 @Test void serverRejectsMultipleCategories(){assertThrows(IllegalArgumentException.class,()->CustomItemCategory.requireSingle(true,false,true));CustomItemCategory.requireSingle(false,true,null);}
}
