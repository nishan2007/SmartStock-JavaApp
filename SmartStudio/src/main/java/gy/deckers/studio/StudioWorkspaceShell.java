package gy.deckers.studio;

import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import javax.swing.*;
import java.awt.*;

/** Canvas-first layout. Existing editor controls retain their listeners and models. */
final class StudioWorkspaceShell extends JPanel {
    private final JPanel inspector = DeckersSwing.panel();
    private final JTabbedPane sections = new JTabbedPane();
    private final JPanel toolbar = DeckersSwing.panel();
    private final JPanel top=new JPanel(new BorderLayout());
    private final JPanel rail = DeckersSwing.panel();
    private final JSplitPane workspace;
    private int inspectorWidth=300;

    StudioWorkspaceShell(JComponent canvas) {
        super(new BorderLayout(8, 8));
        setOpaque(false);
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        inspector.setLayout(new BorderLayout());
        inspector.setPreferredSize(new Dimension(300, 300));
        inspector.setMinimumSize(new Dimension(160,100));
        inspector.add(sections);
        JButton hide = button("Hide panel", () -> showInspector(false));
        inspector.add(hide, BorderLayout.SOUTH);
        top.setOpaque(false);top.add(toolbar,BorderLayout.NORTH);add(top,BorderLayout.NORTH);
        add(rail, BorderLayout.WEST);
        canvas.setMinimumSize(new Dimension(180,100));
        workspace=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,canvas,inspector);
        workspace.setResizeWeight(1);workspace.setContinuousLayout(true);workspace.setBorder(null);
        workspace.setDividerSize(8);
        add(workspace, BorderLayout.CENTER);
        showInspector(false);
    }

    void addSection(String name, JComponent component) {
        JPanel panel = DeckersSwing.panel();
        panel.setLayout(new BorderLayout());
        panel.add(component);
        sections.addTab(name, panel);
        JButton toggle = button(name, () -> {
            boolean same = inspector.isVisible() && sections.getSelectedComponent() == panel;
            sections.setSelectedComponent(panel);
            showInspector(!same);
        });
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        rail.add(toggle);
        rail.add(Box.createVerticalStrut(6));
    }

    void showInspector(boolean visible) {
        if(!visible&&inspector.isVisible()&&inspector.getWidth()>0)inspectorWidth=inspector.getWidth();
        inspector.setVisible(visible);
        workspace.setDividerSize(visible?8:0);
        if(visible){int available=workspace.getWidth();if(available>0)workspace.setDividerLocation(Math.max(180,available-inspectorWidth-8));else workspace.setDividerLocation(.7);}
        revalidate();
        repaint();
    }

    void addTextTools(JComponent controls){JScrollPane scroll=new JScrollPane(controls,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);scroll.setBorder(null);scroll.setPreferredSize(new Dimension(300,54));top.add(scroll,BorderLayout.SOUTH);}

    void addTools(Component controls) { toolbar.add(controls); }

    void addPrimaryAction(String title,Runnable action){
        JPanel actions=DeckersSwing.panel();actions.setLayout(new FlowLayout(FlowLayout.LEFT,6,2));
        actions.add(button(title,action));
        top.remove(toolbar);top.add(actions,BorderLayout.NORTH);top.add(toolbar,BorderLayout.CENTER);
    }

    static JComponent properties(JTable table) {
        JPanel fields=DeckersSwing.panel();fields.setLayout(new GridLayout(0,1,4,4));
        final long[] revision={0};
        Runnable refresh=()->{
            fields.removeAll();int row=table.getSelectedRow();
            if(row<0 || row>=table.getRowCount()){fields.add(new JLabel("Select an object in the canvas or Layers."));}
            else for(int column=0;column<table.getModel().getColumnCount();column++){
                final int modelRow=table.convertRowIndexToModel(row),modelColumn=column;
                final long version=revision[0];
                fields.add(new JLabel(table.getModel().getColumnName(column)));
                Object value=table.getModel().getValueAt(modelRow,column);
                JTextField input=new JTextField(value==null?"":String.valueOf(value));DeckersSwing.styleField(input);
                input.setEditable(table.getModel().isCellEditable(modelRow,column));
                Runnable commit=()->{
                    if(version!=revision[0] || modelRow>=table.getModel().getRowCount() || table.getSelectedRow()!=row)return;
                    Object old=table.getModel().getValueAt(modelRow,modelColumn);
                    if(!String.valueOf(old==null?"":old).equals(input.getText()))table.getModel().setValueAt(input.getText(),modelRow,modelColumn);
                };
                input.addActionListener(e->commit.run());
                input.addFocusListener(new java.awt.event.FocusAdapter(){public void focusLost(java.awt.event.FocusEvent e){commit.run();}});
                fields.add(input);
            }
            fields.revalidate();fields.repaint();
        };
        table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())refresh.run();});
        table.getModel().addTableModelListener(e->{revision[0]++;SwingUtilities.invokeLater(refresh);});
        refresh.run();
        JPanel holder=DeckersSwing.panel();holder.setLayout(new BorderLayout());holder.add(fields,BorderLayout.NORTH);
        return new JScrollPane(holder);
    }

    static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        DeckersSwing.styleUtilityButton(button, DeckersPalette.PURPLE);
        button.addActionListener(e -> action.run());
        return button;
    }

    /** Popovers preserve every existing action without permanent toolbar rows. */
    static JButton menu(String title, JComponent content) {
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.add(content);
        closeAfterAction(content,popup);
        JButton trigger = button(title + " ▾", () -> {});
        trigger.setComponentPopupMenu(popup);
        trigger.addActionListener(e -> popup.show(trigger, 0, trigger.getHeight()));
        return trigger;
    }

    static void stack(JPanel panel) {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (Component child : panel.getComponents()) {
            if (child instanceof JComponent component) {
                component.setAlignmentX(Component.LEFT_ALIGNMENT);
                component.setMaximumSize(new Dimension(Integer.MAX_VALUE,component.getPreferredSize().height));
            }
        }
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE,panel.getPreferredSize().height));
    }

    private static void closeAfterAction(Container content,JPopupMenu popup){
        for(Component child:content.getComponents()){
            if(child instanceof JButton button)button.addActionListener(e->popup.setVisible(false));
            else if(child instanceof Container nested)closeAfterAction(nested,popup);
        }
    }
}
