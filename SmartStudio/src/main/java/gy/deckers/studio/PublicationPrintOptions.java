package gy.deckers.studio;

import publishing.Publication;
import javax.swing.*;
import javax.print.attribute.standard.Sides;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Per-job document-page selection; never changes the saved template. */
final class PublicationPrintOptions extends JPanel {
    final List<JCheckBox> pages=new ArrayList<>();
    final JCheckBox duplex=new JCheckBox("Print front and back (two-sided)",false);
    final JComboBox<String> edge=new JComboBox<>(new String[]{"Flip on long edge","Flip on short edge"});
    PublicationPrintOptions(Publication source,int current){
        super(new BorderLayout(8,8));
        JPanel choices=new JPanel();choices.setLayout(new BoxLayout(choices,BoxLayout.Y_AXIS));
        for(int i=0;i<source.pages.size();i++){
            JCheckBox page=new JCheckBox((i+1)+" • "+source.pages.get(i).name,!source.pages.get(i).reference);
            page.setEnabled(!source.pages.get(i).reference);pages.add(page);choices.add(page);
        }
        JScrollPane scroll=new JScrollPane(choices);scroll.setPreferredSize(new Dimension(420,Math.min(260,Math.max(65,pages.size()*28))));add(scroll);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));
        for(String label:new String[]{"All pages","Current page","Clear"}){
            JButton button=new JButton(label);buttons.add(button);button.addActionListener(e->{for(int i=0;i<pages.size();i++)pages.get(i).setSelected(pages.get(i).isEnabled()&&(label.equals("All pages")||(label.equals("Current page")&&i==current)));});
        }
        add(buttons,BorderLayout.NORTH);
        JPanel bottom=new JPanel(new GridLayout(0,1,4,4));bottom.add(duplex);bottom.add(edge);edge.setEnabled(false);
        duplex.addActionListener(e->edge.setEnabled(duplex.isSelected()));
        bottom.add(new JLabel("Default: single-sided. Checked pages print in document order."));
        bottom.add(new JLabel("Two-sided pairs checked pages in order; an unpaired last page has a blank back."));add(bottom,BorderLayout.SOUTH);
    }
    Publication selection(Publication source){
        List<Integer> selected=new ArrayList<>();for(int i=0;i<pages.size();i++)if(pages.get(i).isEnabled()&&pages.get(i).isSelected())selected.add(i);
        return select(source,selected,duplex.isSelected(),edge.getSelectedIndex()==1);
    }
    static Publication select(Publication source,List<Integer> indices,boolean duplex,boolean shortEdge){
        if(indices.isEmpty())throw new IllegalArgumentException("Select at least one page to print.");
        if(duplex&&indices.size()<2)throw new IllegalArgumentException("Select at least two pages for front and back printing.");
        Publication result=source.copy();var all=new ArrayList<>(result.pages);result.pages.clear();int previous=-1;
        for(int i:indices){if(i<=previous||i<0||i>=all.size()||all.get(i).reference)throw new IllegalArgumentException("Invalid print page selection.");result.pages.add(all.get(i));previous=i;}
        result.print.duplex=duplex;result.print.booklet=false;result.print.backMapping=(shortEdge^(result.print.width>result.print.height))?"SHORT_EDGE":"LONG_EDGE";
        if(duplex)result.print.mixPages=false;
        result.validate();return result;
    }
    static Publication choose(Component owner,Publication source,int current){
        return choose(owner,source,current,false);
    }
    static Publication chooseRiso(Component owner,Publication source,int current){
        return choose(owner,source,current,true);
    }
    private static Publication choose(Component owner,Publication source,int current,boolean riso){
        PublicationPrintOptions options=new PublicationPrintOptions(source,current);
        if(riso){JPanel bottom=(JPanel)options.duplex.getParent();bottom.removeAll();bottom.add(new JLabel("Risograph masters print single-sided. Choose the source pages to separate."));}
        while(JOptionPane.showConfirmDialog(owner,options,"Pages and sides to print",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
            try{return options.selection(source);}catch(IllegalArgumentException ex){JOptionPane.showMessageDialog(owner,ex.getMessage(),"Print selection",JOptionPane.WARNING_MESSAGE);}
        }
        return null;
    }
    static Sides sides(Publication p){return !p.print.duplex?Sides.ONE_SIDED:("SHORT_EDGE".equals(p.print.backMapping)^(p.print.width>p.print.height))?Sides.TWO_SIDED_SHORT_EDGE:Sides.TWO_SIDED_LONG_EDGE;}
}
