package ui.screens;

import com.google.gson.JsonObject;
import services.OverseasCashService;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;

/** Independent of normal CF, float and drawer counts. */
final class OverseasCashChecker extends JPanel {
    private boolean saved,updating;
    private BigDecimal expected=BigDecimal.ZERO;
    private final JLabel totals=new JLabel("No overseas cash balance loaded");
    private final JTextField reason=new JTextField();
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"GYD note","Quantity","Amount"},0){
        public boolean isCellEditable(int row,int col){return !saved&&col==1;}
    };
    private final JTable table=new JTable(model);
    OverseasCashChecker(){
        super(new BorderLayout(6,6));setOpaque(false);setBorder(BorderFactory.createTitledBorder("Overseas Cash Checker"));
        table.setRowHeight(22);table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(330,175));add(scroll,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new GridLayout(0,1,0,3));bottom.setOpaque(false);bottom.add(totals);bottom.add(new JLabel("Shortage / overage reason"));bottom.add(reason);add(bottom,BorderLayout.SOUTH);
        model.addTableModelListener(e->{if(!updating)recalculate();});
    }
    void display(OverseasCashService.FundReport fund,boolean saved){
        this.saved=saved;expected=fund==null?BigDecimal.ZERO:fund.carriedForward();updating=true;model.setRowCount(0);
        var check=fund==null?null:fund.verification();
        for(int d:OverseasCashService.COUNT_DENOMINATIONS)model.addRow(new Object[]{d,check==null?0:check.denominations().getOrDefault(Integer.toString(d),0),""});
        reason.setText(check==null?"":check.reason());reason.setEditable(!saved);table.setEnabled(fund!=null&&(!saved||check!=null));updating=false;
        recalculate();if(saved&&check==null)totals.setText("No overseas cash count saved for this submission.");
    }
    private BigDecimal recalculate(){
        updating=true;BigDecimal count=BigDecimal.ZERO;boolean valid=true;
        try{for(int row=0;row<model.getRowCount();row++){
            try{int q=new BigDecimal(String.valueOf(model.getValueAt(row,1)).trim()).intValueExact();if(q<0)throw new IllegalArgumentException();
                BigDecimal amount=BigDecimal.valueOf(OverseasCashService.COUNT_DENOMINATIONS.get(row)).multiply(BigDecimal.valueOf(q));count=count.add(amount);model.setValueAt(amount.toPlainString(),row,2);
            }catch(Exception e){valid=false;model.setValueAt("Invalid quantity",row,2);}
        }}finally{updating=false;}
        BigDecimal difference=count.subtract(expected);
        totals.setText(valid?"<html>Expected: GYD "+expected.toPlainString()+" &nbsp; Counted: GYD "+count.toPlainString()+"<br>"+(difference.signum()==0?"Balanced":difference.signum()<0?"Missing: GYD "+difference.abs().toPlainString():"Over: GYD "+difference.toPlainString())+"</html>":"Enter non-negative whole quantities.");
        return valid?count:null;
    }
    JsonObject submission() {
        if(table.isEditing()&&!table.getCellEditor().stopCellEditing())throw new IllegalArgumentException("Finish entering the overseas cash count.");
        BigDecimal count=recalculate();if(count==null)throw new IllegalArgumentException("Correct the overseas cash quantities.");
        if(count.compareTo(expected)!=0&&reason.getText().isBlank())throw new IllegalArgumentException("Enter a reason for the overseas cash shortage or overage.");
        JsonObject input=new JsonObject(),counts=new JsonObject();for(int row=0;row<model.getRowCount();row++)counts.addProperty(OverseasCashService.COUNT_DENOMINATIONS.get(row).toString(),new BigDecimal(model.getValueAt(row,1).toString()).intValueExact());
        input.add("denominations",counts);input.addProperty("expected",expected);input.addProperty("reason",reason.getText().trim());return input;
    }
}
