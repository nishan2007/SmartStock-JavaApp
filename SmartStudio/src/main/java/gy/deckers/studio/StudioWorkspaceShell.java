package gy.deckers.studio;

import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import javax.swing.*;
import java.awt.*;

/** Canvas-first layout. Existing editor controls retain their listeners and models. */
final class StudioWorkspaceShell extends JPanel {
    private final JPanel inspector = DeckersSwing.panel();
    private final JTabbedPane sections = new JTabbedPane();
    private final StudioRibbon ribbon = new StudioRibbon();
    private final JPanel output = DeckersSwing.panel();
    private final JLabel title = new JLabel("Design workspace");
    private final JPanel quickActions = DeckersSwing.panel();
    private final JSplitPane workspace;
    private final CardLayout surfaceLayout=new CardLayout();
    private final JPanel surface=new JPanel(surfaceLayout);
    private StudioRecordBrowser<?> records;
    private int inspectorWidth=360;
    private boolean taskWindows;
    void useTaskWindows(){taskWindows=true;showInspector(false);}

    StudioWorkspaceShell(JComponent canvas) {
        super(new BorderLayout(8, 8));
        setOpaque(false);
        ribbon.responsive();
        output.setLayout(new FlowLayout(FlowLayout.RIGHT,8,0));
        JPanel top=DeckersSwing.panel();top.setLayout(new BorderLayout(8,8));
        title.setVisible(false);
        quickActions.setLayout(new FlowLayout(FlowLayout.LEFT,8,0));quickActions.setVisible(false);
        output.setOpaque(false);ribbon.outputActions(output);
        top.add(ribbon);top.add(quickActions,BorderLayout.SOUTH);add(top,BorderLayout.NORTH);
        inspector.setLayout(new BorderLayout());
        inspector.setPreferredSize(new Dimension(inspectorWidth,300));
        inspector.setMinimumSize(new Dimension(240,100));
        inspector.add(sections);
        inspector.add(button("Hide panel",()->showInspector(false)),BorderLayout.SOUTH);
        canvas.setMinimumSize(new Dimension(180,100));
        workspace=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,canvas,inspector);
        workspace.setResizeWeight(1);workspace.setContinuousLayout(true);workspace.setBorder(null);
        surface.add(workspace,"design");add(surface);showInspector(false);
        ribbon.onTabSelection(tab->{if(!"Data".equals(tab))closeRecords();});
    }

    StudioFileView installFile(StudioFileView.Actions actions){
        StudioFileView view=new StudioFileView(actions,()->ribbon.select("Home"));view.setVisible(false);
        ribbon.installFile(file->{
            if(file&&view.getParent()!=this){
                remove(surface);
                add(view,BorderLayout.CENTER);
                view.setVisible(true);
                view.show("info");
            }else if(!file&&view.getParent()==this){
                view.setVisible(false);
                remove(view);
                add(surface,BorderLayout.CENTER);
            }
            revalidate();
            repaint();
        });
        return view;
    }
    boolean recordsOpen(){return records!=null;}

    <K> void showRecords(StudioRecordBrowser<K> browser,java.util.concurrent.Callable<java.util.List<StudioRecordBrowser.Row<K>>> fetch){
        closeRecords();
        Window active=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if(active instanceof JDialog task&&task.getOwner()==SwingUtilities.getWindowAncestor(this))task.dispose();
        ribbon.select("Data");
        records=browser;
        ribbon.recordsMode(true);
        surface.add(browser,"records");
        surfaceLayout.show(surface,"records");
        SwingUtilities.invokeLater(()->{if(records==browser)browser.start(fetch);});
    }

    void closeRecords(){
        if(records==null)return;
        records.close();
        surface.remove(records);
        records=null;
        ribbon.recordsMode(false);
        surfaceLayout.show(surface,"design");
        surface.revalidate();
        surface.repaint();
    }

    @Override public void removeNotify(){
        closeRecords();
        super.removeNotify();
    }
    StudioRibbon ribbon(){return ribbon;}
    void title(String value){title.setText(value);ribbon.getAccessibleContext().setAccessibleDescription(value+" workspace tools");}
    void quickAction(AbstractButton source){addQuickAction(StudioRibbon.shortcut(source,source.getText()));}
    private void addQuickAction(JButton button){
        button.setIcon(StudioVisuals.commandIcon(button,16));
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setPreferredSize(null);
        Dimension size=button.getPreferredSize();
        button.setPreferredSize(new Dimension(size.width,Math.max(44,size.height)));
        quickActions.add(button);
    }

    @Override public void doLayout(){
        Window window=SwingUtilities.getWindowAncestor(this);
        boolean compact=getWidth()>0&&(getWidth()<1200||(window!=null&&window.getHeight()<620));
        ribbon.autoCompact(compact);quickActions.setVisible(records==null&&compact&&ribbon.isCollapsed()&&quickActions.getComponentCount()>0);
        super.doLayout();
    }

    void addSection(String name,JComponent component){
        JPanel panel=DeckersSwing.panel();panel.setLayout(new BorderLayout());panel.add(component);
        sections.addTab(name,panel);
        String tab=name.equals("Data")?"Data":name.equals("Print setup")?"Print Setup":"View";
        if(taskWindows){if(!name.equals("Data"))ribbon.task(tab,"Tools",name,panel);return;}
        ribbon.add(tab,"Panels",button(name,()->{
            boolean same=inspector.isVisible()&&sections.getSelectedComponent()==panel;
            sections.setSelectedComponent(panel);
            if(isShowing()&&!GraphicsEnvironment.isHeadless()&&getWidth()>0&&getWidth()<1200){StudioRibbon.showTask(this,name,panel);}
            else showInspector(!same);
        }));
        if(name.equals("Print setup")){
            for(Component command:output.getComponents())if(command instanceof AbstractButton source&&source.getText().startsWith("Save"))
                ribbon.add("Print Setup","Template",StudioRibbon.shortcut(source,"Save template setup"));
        }
    }

    void showInspector(boolean visible){
        if(!visible&&inspector.isVisible()&&inspector.getWidth()>0)inspectorWidth=inspector.getWidth();
        inspector.setVisible(visible);workspace.setDividerSize(visible?8:0);
        if(visible){int available=workspace.getWidth();if(available>0)workspace.setDividerLocation(Math.max(180,available-inspectorWidth-8));else workspace.setDividerLocation(.7);}
        revalidate();repaint();
    }

    void addTextTools(JComponent controls,java.util.function.Consumer<StudioLayerOrder.Move> reorder){
        ribbon.add("Home","Text",StudioRibbon.button("Settings",ribbon::selectFormatting));
        for(Component c:controls.getComponents())if(c instanceof JComponent editor){
            String tab=editor instanceof TextFormattingToolbar?"Text format":editor instanceof ImageFormattingToolbar?"Picture format":"Code format";
            if(editor instanceof TextFormattingToolbar||editor instanceof ImageFormattingToolbar){
                addLayerOrderTools(editor,reorder);
                ribbon.inline(tab,editor);
            }
            else {controls.remove(editor);editor.setVisible(true);ribbon.task(tab,"Selection","Format selection",editor);}
            ribbon.available(tab,Boolean.TRUE.equals(editor.getClientProperty("studio.selection.available")));
            editor.addPropertyChangeListener("studio.selection.available",e->ribbon.available(tab,Boolean.TRUE.equals(e.getNewValue())));
            if(editor instanceof TextFormattingToolbar text)ribbon.add("Home","Text",text.quickControls());
        }
    }

    private static void addLayerOrderTools(JComponent editor,java.util.function.Consumer<StudioLayerOrder.Move> reorder){
        JPopupMenu menu=new JPopupMenu();
        for(var move:StudioLayerOrder.Move.values()){
            String label=StudioCommand.layerOrder(move).label;
            JMenuItem item=new JMenuItem(label);item.addActionListener(e->reorder.accept(move));menu.add(item);
        }
        JButton button=new JButton("Layer order…");styleButton(button);
        button.putClientProperty("studio.layerOrder",Boolean.TRUE);
        button.setComponentPopupMenu(menu);
        button.addActionListener(e->menu.show(button,0,button.getHeight()));
        editor.add(button);
    }

    static JButton alignmentMenu(java.util.function.IntSupplier selectionSize,
                                 java.util.function.Consumer<StudioArrange.Operation> arrange){
        JPopupMenu menu=new JPopupMenu();
        var operations=new StudioArrange.Operation[]{StudioArrange.Operation.LEFT,StudioArrange.Operation.RIGHT,
                StudioArrange.Operation.TOP,StudioArrange.Operation.BOTTOM,StudioArrange.Operation.TEMPLATE_HORIZONTAL,
                StudioArrange.Operation.TEMPLATE_VERTICAL};
        String[] labels={"Align left edges","Align right edges","Align top edges","Align bottom edges",
                "Center horizontally on template","Center vertically on template"};
        JMenuItem[] items=new JMenuItem[operations.length];
        for(int i=0;i<operations.length;i++){
            var operation=operations[i];
            if(operation==StudioArrange.Operation.TEMPLATE_HORIZONTAL)menu.addSeparator();
            JMenuItem item=new JMenuItem(labels[i]);items[i]=item;
            item.addActionListener(e->arrange.accept(operation));menu.add(item);
        }
        JButton button=new JButton("Align…");
        styleButton(button);
        button.addActionListener(e->{
            int selected=selectionSize.getAsInt();
            for(int i=0;i<items.length;i++)items[i].setEnabled(selected>=operations[i].minimumSelection());
            menu.show(button,0,button.getHeight());
        });
        return button;
    }

    void addTools(Component controls){
        if(controls instanceof JComponent jc&&jc.getClientProperty("studio.menu.content") instanceof JComponent content){
            ribbon.retain(content);
            String title=String.valueOf(jc.getClientProperty("studio.menu.title"));
            if(title.equals("Template & insert")||title.equals("Template")){
                ribbon.task("Layout","Templates","Template files",content);

            }else if(title.equals("Output")){
                registerOutput(content);
                ribbon.task("Print Setup","Output","Output settings",content);
            }else if(title.equals("Riso")){
                for(Component c:content.getComponents())if(c instanceof AbstractButton command)
                    ribbon.add("Riso","Separation",StudioRibbon.shortcut(command,command.getText()));
            }else ribbon.task("Home",title,title,content);
        }else if(controls instanceof AbstractButton button){
            String name=button.getText();
            if(name.startsWith("Save")||name.startsWith("Print")||name.startsWith("Export PDF")){
                StudioVisuals.headerButton(button);addOutputAction(button);
            }else if(name.equals("+")||name.equals("−")||name.equals("Fit"))ribbon.add("View","Zoom",controls);
            else ribbon.add("Home",name.startsWith("Undo")||name.startsWith("Redo")?"History":"Edit",controls);
        }else if(controls instanceof JPanel panel){
            for(Component child:panel.getComponents())addTools(child);
        }else ribbon.add("View","Preview",controls);
    }

    private static int outputRank(AbstractButton button){String name=button.getText();return name.startsWith("Save")?0:name.startsWith("Print")?1:2;}
    private void addOutputAction(AbstractButton button){
        int index=0;while(index<output.getComponentCount()&&output.getComponent(index) instanceof AbstractButton existing&&outputRank(existing)<=outputRank(button))index++;
        output.add(button,index);
    }
    private void registerOutput(Container content){
        for(Component c:content.getComponents()){
            if(c instanceof AbstractButton command&&(command.getText().startsWith("Print")||command.getText().startsWith("Save")||command.getText().equals("Export PDF"))){JButton action=StudioRibbon.shortcut(command,command.getText().startsWith("Print")?"Print":command.getText());StudioVisuals.headerButton(action);addOutputAction(action);}
            else if(c instanceof Container nested)registerOutput(nested);
        }
    }

    void addTextAndImage(AbstractButton text, AbstractButton image){
        ribbon.add(StudioCommand.TEXT,text);
        ribbon.add(StudioCommand.IMAGE,image);
        ribbon.add(StudioCommand.QUICK_TEXT,text);
        ribbon.add(StudioCommand.QUICK_IMAGE,image);
        addQuickAction(StudioCommand.QUICK_TEXT.shortcut(text));
        addQuickAction(StudioCommand.QUICK_IMAGE.shortcut(image));
    }

    void addArrangement(java.util.function.Consumer<StudioLayerOrder.Move> order, Runnable align, Runnable distribute){
        for(var move:StudioLayerOrder.Move.values())ribbon.add(StudioCommand.layerOrder(move),()->order.accept(move));
        ribbon.add(StudioCommand.ALIGN,align);
        ribbon.add(StudioCommand.DISTRIBUTE,distribute);
    }

    void addZoom(Runnable out,Runnable fit,Runnable in){
        ribbon.add(StudioCommand.ZOOM_OUT,out);
        ribbon.add(StudioCommand.FIT,fit);
        ribbon.add(StudioCommand.ZOOM_IN,in);
    }
    void addPrimaryAction(String title,Runnable action){ribbon.add("Layout","Template",button(title,action));}
    static void styleButton(AbstractButton button){
        StudioVisuals.utilityButton(button);
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

    static JButton button(String text,Runnable action){return StudioRibbon.button(text,action);}

    /** A labelled, bounded task window replaces dense popup menus. */
    static JButton menu(String title,JComponent content){
        JButton trigger=button(title+"…",()->StudioRibbon.showTask(content,title,content));
        trigger.putClientProperty("studio.menu.title",title);
        trigger.putClientProperty("studio.menu.content",content);
        // Use the trigger as owner: content may not currently belong to a visible window.
        for(var listener:trigger.getActionListeners())trigger.removeActionListener(listener);
        trigger.addActionListener(e->StudioRibbon.showTask(trigger,title,content));
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
