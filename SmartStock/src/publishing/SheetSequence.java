package publishing;

import java.util.*;

public final class SheetSequence {
  private SheetSequence() {}
  public static final List<String> ORDERS=List.of("NATIVE","ROWS_LEFT_TOP","ROWS_RIGHT_TOP","ROWS_LEFT_BOTTOM","ROWS_RIGHT_BOTTOM","COLUMNS_TOP_LEFT","COLUMNS_TOP_RIGHT","COLUMNS_BOTTOM_LEFT","COLUMNS_BOTTOM_RIGHT","CUSTOM");

  public static List<SheetLayout.Placement> arrange(List<SheetLayout.Placement> placements,String order,String custom){
    if(!ORDERS.contains(order))throw new IllegalArgumentException("Unknown sheet position order.");
    if(order.equals("NATIVE"))return List.copyOf(placements);
    var horizontal=Comparator.comparingDouble(SheetLayout.Placement::x);
    var vertical=Comparator.comparingDouble(SheetLayout.Placement::y);
    var result=new ArrayList<>(placements);
    if(order.equals("CUSTOM")){
      result.sort(vertical.thenComparing(horizontal));
      String[] positions=custom.trim().split("\\s*,\\s*",-1);
      if(positions.length!=result.size())throw new IllegalArgumentException("Custom order must list every sheet position once (1 to "+result.size()+"). Use Position map to check the layout.");
      var selected=new ArrayList<SheetLayout.Placement>();var seen=new HashSet<Integer>();
      for(String position:positions){
        int index;
        try{index=Integer.parseInt(position)-1;}catch(NumberFormatException ex){throw new IllegalArgumentException("Custom order must contain comma-separated position numbers.");}
        if(index<0||index>=result.size()||!seen.add(index))throw new IllegalArgumentException("Custom order must list every sheet position exactly once.");
        selected.add(result.get(index));
      }
      return List.copyOf(selected);
    }
    if(order.contains("RIGHT"))horizontal=horizontal.reversed();
    if(order.contains("BOTTOM"))vertical=vertical.reversed();
    result.sort(order.startsWith("ROWS")?vertical.thenComparing(horizontal):horizontal.thenComparing(vertical));
    return List.copyOf(result);
  }

  public static int instance(int sheet,int position,int capacity,int sheets,boolean cutAndStack){
    return cutAndStack?position*sheets+sheet:sheet*capacity+position;
  }
}
