package ui.screens;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Exercises native page navigation and the existing API contract, without a live store. */
class OverseasOrdersWorkspaceTest {
    private OverseasOrders frame;
    private final List<JsonObject> requests=Collections.synchronizedList(new ArrayList<>());
    private JsonObject current;
    @BeforeEach void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        current=OverseasOrdersPreview.sample();
        frame=edt(()->{
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            OverseasOrders f=new OverseasOrders(this::send);ui.helpers.ThemeManager.applyToWindow(f);f.addNotify();f.validate();return f;
        });awaitIdle();
    }
    @AfterEach void close()throws Exception {if(frame!=null)edt(()->{frame.dispose();return null;});}
    private JsonObject send(JsonObject b) {
        requests.add(b.deepCopy());
        switch(b.get("action").getAsString()) {
            case "LIST":return OverseasOrdersPreview.listing();
            case "SELLERS":return JsonParser.parseString("{\"sellers\":[\"Amazon\"]}").getAsJsonObject();
            case "CUSTOMER_PHONE":return JsonParser.parseString("{\"customers\":[],\"reserved\":false}").getAsJsonObject();
            case "UPLOAD":return JsonParser.parseString("{\"reference\":\"image:test\"}").getAsJsonObject();
            case "SAVE": {
                JsonObject saved=b.getAsJsonObject("order").deepCopy();
                saved.addProperty("overseas_order_id","00000000-0000-0000-0000-000000000001");
                saved.addProperty("revision",current.getAsJsonObject("order").get("revision").getAsInt()+1);
                saved.addProperty("state","DRAFT");saved.add("custom_order_id",JsonNull.INSTANCE);
                saved.addProperty("number","Draft");saved.addProperty("total",40000);saved.addProperty("paid",0);saved.addProperty("balance",40000);
                current.add("order",saved);return current.deepCopy();
            }
            case "ISSUE":current.getAsJsonObject("order").addProperty("state","ISSUED");current.getAsJsonObject("order").addProperty("custom_order_id",241);return current.deepCopy();
            default:return current.deepCopy();
        }
    }
    @Test void listOpensDetailAndPreservesSearchAndSelectionOnReturn()throws Exception {
        edt(()->{
            ((JTextField)member("search")).setText("Sayresh");
            JTable table=(JTable)member("orders");table.setRowSelectionInterval(0,0);
            table.getActionMap().get("openOrder").actionPerformed(null);return null;
        });awaitIdle();
        assertEquals("DETAIL",edt(()->member("page").toString()));
        assertNotNull(edt(()->OverseasOrdersPreview.findButton(frame.getContentPane(),"Take payment")));
        edt(()->{invoke("backToList");return null;});awaitIdle();
        assertEquals("LIST",edt(()->member("page").toString()));
        assertEquals("Sayresh",edt(()->((JTextField)member("search")).getText()));
        assertEquals(0,edt(()->((JTable)member("orders")).getSelectedRow()));
    }
    @Test void itemEditorRetainsQuoteAndAddsCalculatedItemWithoutCallingServer()throws Exception {
        edt(()->{invoke("fresh");((JTextField)member("name")).setText("New customer");((JTextField)member("phone")).setText("+5926001234");invoke("editLine",new Class<?>[]{int.class},-1);return null;});
        edt(()->{
            Map<String,JTextField> fields=fields();fields.get("description").setText("Shoes");fields.get("quantity").setText("2");fields.get("unit_price").setText("1,250.50");OverseasOrdersPreview.findButton(frame.getContentPane(),"Add to quote").doClick();return null;
        });
        assertEquals("QUOTE",edt(()->member("page").toString()));
        assertEquals("New customer",edt(()->((JTextField)member("name")).getText()));
        assertEquals("GYD 2,501.00",edt(()->((JLabel)member("totals")).getText()));
        assertFalse(requests.stream().anyMatch(r->Set.of("SAVE","UPLOAD","ISSUE").contains(r.get("action").getAsString())));
        edt(()->{invoke("editLine",new Class<?>[]{int.class},0);invoke("cancelItem");return null;});
        assertEquals("QUOTE",edt(()->member("page").toString()));
    }
    @Test void saveUploadsStagedScreenshotBeforeIssueWithLatestRevision()throws Exception {
        edt(()->{
            JsonObject draft=current.deepCopy();draft.getAsJsonObject("order").addProperty("state","DRAFT");draft.getAsJsonObject("order").add("custom_order_id",JsonNull.INSTANCE);
            invoke("showDetail",new Class<?>[]{JsonObject.class},draft);
            @SuppressWarnings("unchecked") Map<Integer,byte[]> images=(Map<Integer,byte[]>)member("stagedImages");images.put(0,new byte[]{1,2,3});OverseasOrdersPreview.findButton(frame.getContentPane(),"Issue quote").doClick();return null;
        });awaitIdle();
        List<JsonObject> writes=requests.stream().filter(r->Set.of("SAVE","UPLOAD","ISSUE").contains(r.get("action").getAsString())).toList();
        assertEquals(List.of("SAVE","UPLOAD","SAVE","ISSUE"),writes.stream().map(r->r.get("action").getAsString()).toList());
        assertEquals("image:test",writes.get(2).getAsJsonObject("order").getAsJsonArray("lines").get(0).getAsJsonObject().get("screenshot").getAsString());
        assertEquals(writes.get(2).get("revision").getAsInt()+1,writes.get(3).get("revision").getAsInt());
        assertEquals(4,writes.stream().map(r->r.get("requestId").getAsString()).distinct().count());
        assertEquals("DETAIL",edt(()->member("page").toString()));
        assertTrue(edt(()->((Map<?,?>)member("stagedImages")).isEmpty()));
    }
    @Test void serverPermissionsDisableQuoteAndPaymentActions()throws Exception {
        edt(()->{JsonObject readOnly=current.deepCopy();readOnly.add("permissions",JsonParser.parseString("[\"OVERSEAS_ORDERS_VIEW\"]"));invoke("showDetail",new Class<?>[]{JsonObject.class},readOnly);return null;});
        assertFalse(edt(()->OverseasOrdersPreview.findButton(frame.getContentPane(),"Take payment").isEnabled()));
        edt(()->{invoke("backToList");return null;});awaitIdle();
    }
    @Test void itemValidationKeepsInvalidDataInEditor()throws Exception {
        edt(()->{invoke("fresh");invoke("editLine",new Class<?>[]{int.class},-1);fields().get("description").setText("Shoes");return null;});
        for(String invalid:List.of("0","-1","1.5")){
            edt(()->{fields().get("quantity").setText(invalid);java.lang.reflect.InvocationTargetException error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->invoke("acceptItem"));assertInstanceOf(IllegalArgumentException.class,error.getCause());return null;});
        }
        edt(()->{fields().get("quantity").setText("1");fields().get("product_url").setText("www.example.com");java.lang.reflect.InvocationTargetException error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->invoke("acceptItem"));assertInstanceOf(IllegalArgumentException.class,error.getCause());return null;});
        assertEquals("ITEM",edt(()->member("page").toString()));
        assertTrue(edt(()->((List<?>)member("lineValues")).isEmpty()));
    }
    @Test void compactWindowUsesVerticalScrollingWithoutClippingSummary()throws Exception {
        edt(()->{frame.setSize(1024,768);invoke("showDetail",new Class<?>[]{JsonObject.class},current);frame.validate();return null;});
        Thread.sleep(250);
        edt(()->{frame.validate();JPanel body=(JPanel)member("body");JScrollPane scroll=(JScrollPane)body.getComponent(0);assertFalse(scroll.getHorizontalScrollBar().isVisible());JButton payment=OverseasOrdersPreview.findButton(frame.getContentPane(),"Take payment");assertNotNull(payment);Rectangle bounds=SwingUtilities.convertRectangle(payment.getParent(),payment.getBounds(),scroll.getViewport().getView());assertTrue(bounds.x>=0);assertTrue(bounds.x+bounds.width<=scroll.getViewport().getExtentSize().width);assertTrue(scroll.getVerticalScrollBar().isVisible());return null;});
    }
    @Test void quoteTableRetainsItsRowsUnderTheNativeWindowsTheme()throws Exception {
        edt(()->{JsonObject draft=current.deepCopy();draft.getAsJsonObject("order").addProperty("state","DRAFT");draft.getAsJsonObject("order").add("custom_order_id",JsonNull.INSTANCE);invoke("showDetail",new Class<?>[]{JsonObject.class},draft);frame.validate();return null;});
        Thread.sleep(250);
        edt(()->{frame.validate();JTable table=(JTable)member("items");assertEquals(3,table.getRowCount());assertTrue(table.getParent().getHeight()>=table.getRowHeight()*3,"Native theme must not collapse the item viewport");assertNotNull(OverseasOrdersPreview.findButton(frame.getContentPane(),"Issue quote"));return null;});
    }
    private Object member(String name)throws Exception{return OverseasOrdersPreview.member(frame,name);}
    private void invoke(String name)throws Exception {invoke(name,new Class<?>[0]);}
    private void invoke(String name,Class<?>[] types,Object...args)throws Exception{OverseasOrdersPreview.invoke(frame,name,types,args);}
    @SuppressWarnings("unchecked") private Map<String,JTextField> fields()throws Exception{return (Map<String,JTextField>)member("itemFields");}
    private void awaitIdle()throws Exception {long end=System.nanoTime()+10_000_000_000L;while(System.nanoTime()<end){if(edt(()->!(boolean)member("working"))){edt(()->null);return;}Thread.sleep(25);}fail("API worker timed out");}
    private static <T>T edt(Callable<T> action)throws Exception {
        java.util.concurrent.FutureTask<T> task=new java.util.concurrent.FutureTask<>(action);SwingUtilities.invokeAndWait(task);return task.get();
    }
}
