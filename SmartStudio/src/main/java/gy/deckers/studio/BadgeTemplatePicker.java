package gy.deckers.studio;

import ui.design.DeckersSwing;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Search is local to the chooser; filtered rows retain their original identity. */
final class BadgeTemplatePicker extends JPanel {
    enum Kind { CURRENT, BUILTIN, BLANK, IMPORT, FILE }
    record Choice(String label, Kind kind, Path file) {
        @Override public String toString(){return label;}
    }
    private final JTextField search=new JTextField();
    private final DefaultListModel<Choice> matches=new DefaultListModel<>();
    private final JList<Choice> list=new JList<>(matches);
    private final JLabel count=new JLabel();
    private List<Choice> choices;
    private final Consumer<Choice> selected;
    private boolean filtering;

    BadgeTemplatePicker(List<Choice> choices,JComponent preview,Consumer<Choice> selected){
        super(new BorderLayout(12,12));setOpaque(false);
        this.choices=List.copyOf(choices);this.selected=selected;
        JPanel header=DeckersSwing.panel();header.setLayout(new BorderLayout(0,5));
        JLabel label=new JLabel("Search templates");label.setLabelFor(search);header.add(label,BorderLayout.NORTH);
        DeckersSwing.styleField(search);search.setToolTipText("Type any part of a template name");header.add(search);add(header,BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setVisibleRowCount(9);list.setFixedCellHeight(30);
        list.getAccessibleContext().setAccessibleName("Matching templates");
        JScrollPane results=new JScrollPane(list);results.setPreferredSize(new Dimension(250,280));
        JPanel body=DeckersSwing.panel();body.setLayout(new BorderLayout(12,0));body.add(results,BorderLayout.WEST);body.add(preview);add(body);
        add(count,BorderLayout.SOUTH);
        list.addListSelectionListener(e->{if(!filtering&&!e.getValueIsAdjusting())selectionChanged();});
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}public void changedUpdate(DocumentEvent e){filter();}});
        filter();
    }
    Choice selection(){return list.getSelectedValue();}
    void replaceChoices(List<Choice> values){choices=List.copyOf(values);filter();}
    int resultCount(){return matches.size();}
    void search(String text){search.setText(text);}
    void focusSearch(){search.requestFocusInWindow();}
    private void filter(){
        Choice prior=selection();String query=search.getText().strip().toLowerCase(Locale.ROOT);
        filtering=true;
        try{
            matches.clear();
            for(Choice choice:choices){String name=choice.label().toLowerCase(Locale.ROOT);boolean match=true;for(String word:query.split("\\s+"))if(!name.contains(word)){match=false;break;}if(match)matches.addElement(choice);}
            if(prior!=null&&matches.contains(prior))list.setSelectedValue(prior,true);else if(!matches.isEmpty())list.setSelectedIndex(0);
        }finally{filtering=false;}
        count.setText(matches.isEmpty()?"No matching templates. Try another name or clear the search.":matches.size()+" matching "+(matches.size()==1?"choice":"choices"));
        selectionChanged();
    }
    private void selectionChanged(){selected.accept(selection());firePropertyChange("selectionAvailable",null,selection()!=null);}
}
