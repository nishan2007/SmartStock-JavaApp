package gy.deckers.studio;

import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Studio-only presentation of existing commands. No document or permission state lives here. */
final class StudioRibbon extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<String, RibbonPage> pages = new LinkedHashMap<>();
    // Keep dialog-owned editors in the workspace lifecycle while they are closed.
    private final JPanel settings = new JPanel();
    private final JButton collapse;
    private final JPanel headerActions=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));
    private java.util.function.Consumer<Boolean> fileSelection;
    private boolean collapsed;
    private Boolean userCollapsed;
    private boolean responsive;
    private boolean recordsMode;
    private boolean openQueued, changingTabs;

    StudioRibbon() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(StudioVisuals.SURFACE);
        tabs.setFont(StudioVisuals.font(Font.PLAIN,18));
        tabs.setBackground(StudioVisuals.SURFACE);
        tabs.setForeground(StudioVisuals.INK);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setUI(new BasicTabbedPaneUI(){
            @Override protected Insets getTabAreaInsets(int placement){
                int width=StudioRibbon.this.getWidth();
                int reserved=headerActions.isVisible()&&!separateHeader(width)?headerActions.getPreferredSize().width+8:0;
                return new Insets(0,0,0,reserved);
            }
            @Override protected int calculateTabHeight(int placement,int index,int fontHeight){return 44;}
            @Override protected Insets getTabInsets(int placement,int index){return new Insets(8,13,8,13);}
            @Override protected void paintTabBackground(Graphics g,int placement,int index,int x,int y,int w,int h,boolean selected){
                g.setColor(StudioVisuals.SURFACE);g.fillRect(x,y,w,h);
                if(selected){g.setColor(new Color(0xF3ECFB));g.fillRoundRect(x+4,y+4,w-8,h-7,9,9);}
            }
            @Override protected void paintTabBorder(Graphics g,int placement,int index,int x,int y,int w,int h,boolean selected){
                if(selected){g.setColor(DeckersPalette.PURPLE);g.fillRoundRect(x+8,y+h-4,w-16,3,3,3);}
            }
            @Override protected void paintText(Graphics g,int placement,Font font,FontMetrics metrics,int index,String title,Rectangle textRect,boolean selected){
                Font face=StudioVisuals.font(selected?Font.BOLD:Font.PLAIN,18);g.setFont(face);g.setColor(selected?StudioVisuals.PURPLE:tabs.isEnabledAt(index)?StudioVisuals.INK:Color.GRAY);
                javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(g,title,-1,textRect.x,textRect.y+metrics.getAscent());
            }
            @Override protected void paintContentBorder(Graphics g,int placement,int selected){}
        });
        tabs.setBorder(BorderFactory.createEmptyBorder());
        tabs.getAccessibleContext().setAccessibleName("Workspace ribbon tabs");
        add(tabs);
        settings.setVisible(false);
        add(settings,BorderLayout.SOUTH);
        collapse = button("Collapse", () -> setCollapsed(!collapsed));
        collapse.setToolTipText("Collapse ribbon; select a tab to open its tools in a window");
        StudioVisuals.utilityButton(collapse);collapse.setText("");collapse.setIcon(StudioVisuals.icon("collapse ribbon",16));
        collapse.setPreferredSize(new Dimension(44,44));
        collapse.getAccessibleContext().setAccessibleName("Collapse ribbon");
        headerActions.setOpaque(false);headerActions.add(collapse);
        add(headerActions,BorderLayout.EAST);
        setComponentZOrder(headerActions,0);
        tabs.addChangeListener(e -> {
            if(fileSelection!=null){boolean file=tabs.getSelectedComponent() instanceof RibbonPage p&&p.tab.equals("File");headerActions.setVisible(!file);firePropertyChange("fileOpen",!file,file);fileSelection.accept(file);}
            if(!changingTabs&&!(KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() instanceof JDialog))openSelected();
        });
        tabs.addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){
            if(collapsed&&tabs.indexAtLocation(e.getX(),e.getY())==tabs.getSelectedIndex())openSelected();
        }});
        tabs.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"openCollapsedTools");
        tabs.getActionMap().put("openCollapsedTools",new AbstractAction(){public void actionPerformed(ActionEvent e){openSelected();}});
    }

    void outputActions(JComponent actions){headerActions.add(actions,0);revalidate();}

    void onTabSelection(java.util.function.Consumer<String> selection){
        tabs.addChangeListener(e->{if(tabs.getSelectedComponent() instanceof RibbonPage page)selection.accept(page.tab);});
    }

    void recordsMode(boolean active){
        recordsMode=active;
        RibbonPage data=pages.get("Data");
        if(data==null)return;
        data.groups.values().forEach(group->group.setVisible(!active||group.title.equals("Records")));
        revalidate();
        repaint();
    }

    @Override public void paint(Graphics original){
        Graphics2D g=(Graphics2D)original.create();
        g.clip(new java.awt.geom.RoundRectangle2D.Double(0,0,getWidth(),getHeight(),12,12));super.paint(g);g.dispose();
    }

    private void openSelected(){
        if(tabs.getSelectedComponent() instanceof RibbonPage p&&p.tab.equals("File"))return;
        if(tabs.getSelectedComponent() instanceof RibbonPage selected&&selected.editor!=null){
            applyCollapsed(false);return;
        }
        if(!collapsed||!isShowing()||openQueued)return;
        openQueued=true;
        SwingUtilities.invokeLater(()->{
            openQueued=false;
            if(collapsed&&isShowing()&&tabs.getSelectedComponent() instanceof RibbonPage page)
                showTask(this,tabs.getTitleAt(tabs.getSelectedIndex()),page.dialogContent());
        });
    }

    void setCollapsed(boolean value) {
        userCollapsed=value;
        applyCollapsed(value);
    }

    void autoCompact(boolean compact){
        if(tabs.getSelectedComponent() instanceof RibbonPage page&&page.editor!=null)return;
        if(userCollapsed==null)applyCollapsed(compact&&!recordsMode);
    }
    void responsive(){responsive=true;}

    @Override public void doLayout(){
        Window window=SwingUtilities.getWindowAncestor(this);
        if(responsive&&window!=null&&!(tabs.getSelectedComponent() instanceof RibbonPage page&&page.editor!=null))autoCompact(getWidth()<1200||window.getHeight()<620);
        super.doLayout();
        // Keep output actions clear of the tab labels on narrow windows.
        int headerHeight=separateHeader(getWidth())?44:0;
        tabs.setBounds(0,headerHeight,getWidth(),Math.max(0,getHeight()-headerHeight));
        int width=headerActions.getPreferredSize().width;
        headerActions.setBounds(Math.max(0,getWidth()-width),0,Math.min(width,getWidth()),44);
    }

    private void applyCollapsed(boolean value){
        if(collapsed==value)return;
        collapsed = value;
        collapse.setIcon(StudioVisuals.icon(value?"expand ribbon":"collapse ribbon",16));
        collapse.setToolTipText(value?"Expand ribbon":"Collapse ribbon");
        collapse.getAccessibleContext().setAccessibleName(value?"Expand ribbon":"Collapse ribbon");
        for (RibbonPage page : pages.values()) page.setCollapsed(value);
        revalidate();
    }

    boolean isCollapsed() { return collapsed; }

    private boolean separateHeader(int width){
        if(width<=0||!headerActions.isVisible())return false;
        FontMetrics metrics=tabs.getFontMetrics(StudioVisuals.font(Font.BOLD,18));
        int needed=headerActions.getPreferredSize().width+8;
        for(int i=0;i<tabs.getTabCount();i++)needed+=metrics.stringWidth(tabs.getTitleAt(i))+26;
        return needed>width;
    }

    @Override public Dimension getPreferredSize(){
        if(tabs==null||collapse==null)return super.getPreferredSize();
        int widthHint=getWidth();
        for(Container parent=getParent();parent!=null;parent=parent.getParent()){
            if(parent instanceof StudioWorkspaceShell&&parent.getWidth()>0){
                widthHint=widthHint>0?Math.min(widthHint,parent.getWidth()):parent.getWidth();
                break;
            }
            if(widthHint<=0)widthHint=parent.getWidth();
        }
        int body=tabs.getSelectedComponent() instanceof RibbonPage page?page.bodyHeight(widthHint>0?widthHint:1100):124;
        return new Dimension(700,44+8+(separateHeader(widthHint)?44:0)+(collapsed?0:body));
    }

    void add(StudioCommand command, AbstractButton source) {
        add(command.tab, command.group, command.shortcut(source));
    }

    void add(StudioCommand command, Runnable action) {
        add(command.tab, command.group, command.button(action));
    }

    void add(String tab, String group, Component control) {
        prepare(control);
        if(control instanceof AbstractButton b)StudioVisuals.ribbonButton(b);
        RibbonPage page = pages.get(tab);
        if (page == null) {
            page = new RibbonPage(tab);
            pages.put(tab, page);
            int at=0;
            while(at<tabs.getTabCount()&&rank(tabs.getTitleAt(at))<=rank(tab))at++;
            Component selected=tabs.getSelectedComponent();
            tabs.insertTab(tab,null,page,null,at);
            if(tab.equals("Home"))tabs.setSelectedComponent(page);
            else if(selected!=null)tabs.setSelectedComponent(selected);
        }
        page.group(group).addControl(control);
    }

    void installFile(java.util.function.Consumer<Boolean> selection){
        fileSelection=selection;Component current=tabs.getSelectedComponent();changingTabs=true;
        try{RibbonPage page=new RibbonPage("File");pages.put("File",page);tabs.insertTab("File",null,page,"Manage template files",0);if(current!=null)tabs.setSelectedComponent(current);}finally{changingTabs=false;}
        select("Home");
    }
    static void prepare(Component control){
        if(control instanceof AbstractButton b){
            StudioWorkspaceShell.styleButton(b);
        }else if(control instanceof JComboBox<?>||control instanceof JSpinner||control instanceof JTextField){
            control.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,15));
            Dimension d=control.getPreferredSize();control.setPreferredSize(new Dimension(Math.min(320,d.width),Math.max(40,d.height)));
        }else if(control instanceof JPanel panel){for(Component child:panel.getComponents())prepare(child);}
        else if(control instanceof JLabel label)label.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,Boolean.TRUE.equals(label.getClientProperty("studio.ribbon.caption"))?12:15));
    }

    private static int rank(String tab){return switch(tab){case "File"->-1;case "Home"->0;case "Insert"->1;case "Layout"->2;case "Print Setup"->3;case "Data"->4;case "View"->5;case "Cut Setup"->6;case "Riso"->7;case "Numbering"->8;case "Process"->9;case "Phones"->10;case "Forms"->11;case "OCR"->12;default->13;};}

    void task(String tab, String group, String title, JComponent content) {
        if(content.getParent()==null)settings.add(content);
        add(tab, group, button(group.equals("Records")?title:title+"…", () -> showTask(this, title, content)));
    }
    void retain(JComponent content){settings.add(content);}

    /** Selection editors stay in the ribbon, including on compact displays. */
    void inline(String tab,JComponent editor){
        add(tab,"Selection",new JLabel());
        RibbonPage page=pages.get(tab);page.removeAll();page.groups.clear();page.editor=editor;
        prepare(editor);
        // Keep the actual selection-bound controls together; their refresh logic
        // and keyboard editing continue to use the same component instances.
        editor.setLayout(new WrapLayout());
        editor.setBorder(BorderFactory.createEmptyBorder(0,4,0,4));
        StudioVisuals.styleTree(editor);
        if(tab.equals("Layout")||tab.equals("Print Setup")||editor instanceof TextFormattingToolbar||editor instanceof ImageFormattingToolbar)StudioRibbonBands.install(editor,tab);
        page.add(editor);editor.setVisible(true);page.revalidate();
    }

    static JPanel field(String label,JComponent control,int width){
        JPanel field=new JPanel(new BorderLayout(0,2));field.setOpaque(false);
        JLabel caption=new JLabel(label);caption.putClientProperty("studio.ribbon.caption",Boolean.TRUE);caption.setLabelFor(control);control.getAccessibleContext().setAccessibleName(label);
        control.setPreferredSize(new Dimension(width,40));field.add(caption,BorderLayout.NORTH);field.add(control);field.setPreferredSize(new Dimension(width,56));return field;
    }
    void inlineSettings(String tab,JPanel controls){
        RibbonPage existing=pages.get(tab);
        if(existing!=null)for(Group group:existing.groups.values())for(Component control:group.body.getComponents()){
            if(control instanceof AbstractButton button&&button.getText().startsWith("Template files")){add("Insert","Templates",control);continue;}
            if(group.title.equals("Arrange")){
                if(control instanceof AbstractButton button&&(button.getText().equals("Bring to front")||button.getText().equals("Send to back")))add("Home","Arrange",control);
                continue;
            }
            if(control instanceof AbstractButton button&&!button.getText().toLowerCase(java.util.Locale.ROOT).contains("size")){
                StudioWorkspaceShell.styleButton(button);button.setIcon(null);button.setVerticalTextPosition(SwingConstants.CENTER);
                button.setPreferredSize(null);controls.add(button);
            }
        }
        inline(tab,controls);if(!(controls.getLayout() instanceof StudioRibbonBands))controls.setLayout(new FlowLayout(FlowLayout.LEFT,6,4));
    }

    void select(String name) {
        RibbonPage page = pages.get(name);
        if (page != null&&tabs.indexOfComponent(page)>=0) {changingTabs=true;try{tabs.setSelectedComponent(page);if(page.editor!=null)applyCollapsed(false);}finally{changingTabs=false;}}
    }

    void selectFormatting(){
        for(var entry:pages.entrySet())if(entry.getKey().endsWith(" format")&&tabs.indexOfComponent(entry.getValue())>=0){select(entry.getKey());return;}
    }

    void available(String tab,boolean visible){
        RibbonPage page=pages.get(tab);if(page==null)return;
        changingTabs=true;
        try{
        int existing=tabs.indexOfComponent(page);
        if(!visible&&existing>=0){boolean active=tabs.getSelectedComponent()==page;tabs.remove(page);if(active)select("Home");}
        else if(visible&&existing<0){
            Component selected=tabs.getSelectedComponent();int at=0;
            while(at<tabs.getTabCount()&&rank(tabs.getTitleAt(at))<=rank(tab))at++;
            tabs.insertTab(tab,null,page,null,at);if(selected!=null)tabs.setSelectedComponent(selected);
        }
        }finally{changingTabs=false;}
    }

    static JButton button(String title, Runnable action) {
        JButton button = new JButton(title);
        DeckersSwing.styleUtilityButton(button, DeckersPalette.PURPLE);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        button.setFocusPainted(true);
        button.setMargin(new Insets(8, 12, 8, 12));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.sectionBorder(DeckersPalette.PURPLE)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        Dimension preferred = button.getPreferredSize();
        button.setPreferredSize(new Dimension(preferred.width, Math.max(44, preferred.height)));
        StudioVisuals.utilityButton(button);
        button.getAccessibleContext().setAccessibleName(title);
        button.addActionListener(e -> action.run());
        return button;
    }

    /** Multiple ribbon locations share one existing command and its enabled state. */
    static JButton shortcut(AbstractButton source, String title) {
        JButton result = button(title, () -> source.doClick(0));
        result.putClientProperty(StudioCommand.class, source.getClientProperty(StudioCommand.class));
        result.setEnabled(source.isEnabled());
        result.setToolTipText(source.getToolTipText()==null?title:source.getToolTipText());
        source.addPropertyChangeListener("enabled", e -> result.setEnabled(source.isEnabled()));
        return result;
    }

    /** Existing settings apply live; Close never falsely promises to roll them back. */
    static void showTask(Component owner, String title, JComponent content) {
        if (GraphicsEnvironment.isHeadless()) return;
        Container previous = content.getParent();
        int index = previous == null ? -1 : previous.getComponentZOrder(content);
        Object constraint = previous != null && previous.getLayout() instanceof BorderLayout border
                ? border.getConstraints(content) : null;
        Window window = SwingUtilities.getWindowAncestor(owner);
        Component previousFocus=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        JDialog dialog = new JDialog(window, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel body = DeckersSwing.panel();
        body.setLayout(new BorderLayout(12, 12));
        body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        body.add(heading, BorderLayout.NORTH);
        prepare(content);wrapRows(content);StudioVisuals.styleTree(content);
        boolean fill=Boolean.TRUE.equals(content.getClientProperty("studio.task.fill"));
        JScrollPane viewport = fill?null:new JScrollPane(new TaskBody(content));
        if(viewport!=null){
            viewport.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            viewport.setBorder(null);
            viewport.getVerticalScrollBar().setUnitIncrement(24);
            body.add(viewport);
        }else body.add(content,BorderLayout.CENTER);
        JButton close = button("Close", dialog::dispose);
        JPanel footer = DeckersSwing.panel();
        footer.setLayout(new BorderLayout(12,0));
        footer.add(new JLabel("Actions apply to the current workspace."));
        footer.add(close,BorderLayout.EAST);
        body.add(footer, BorderLayout.SOUTH);StudioVisuals.styleTree(body);
        dialog.setContentPane(body);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        Rectangle usable = window == null ? GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()
                : window.getGraphicsConfiguration().getBounds();
        if (window != null) {
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(window.getGraphicsConfiguration());
            usable = new Rectangle(usable.x + insets.left, usable.y + insets.top,
                    usable.width - insets.left - insets.right, usable.height - insets.top - insets.bottom);
        }
        dialog.setSize(Math.min(960, usable.width - 32), Math.min(680, usable.height - 32));
        dialog.setLocationRelativeTo(window);
        dialog.setLocation(Math.max(usable.x, Math.min(dialog.getX(), usable.x + usable.width - dialog.getWidth())),
                Math.max(usable.y, Math.min(dialog.getY(), usable.y + usable.height - dialog.getHeight())));
        try { dialog.setVisible(true); }
        finally {
            if(viewport!=null)viewport.setViewportView(null);else body.remove(content);
            if (previous != null) {
                if (constraint != null) previous.add(content, constraint, Math.min(index, previous.getComponentCount()));
                else previous.add(content, Math.min(index, previous.getComponentCount()));
                previous.revalidate();
                previous.repaint();
            }
            if(previousFocus!=null)previousFocus.requestFocusInWindow();else owner.requestFocusInWindow();
        }
    }

    private static void wrapRows(Container content){
        if(content.getLayout() instanceof FlowLayout&&!(content.getLayout() instanceof WrapLayout))content.setLayout(new WrapLayout());
        for(Component c:content.getComponents())if(c instanceof JPanel panel)wrapRows(panel);
    }

    private static final class TaskBody extends JPanel implements Scrollable {
        TaskBody(JComponent content){super(new BorderLayout());setOpaque(false);add(content);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 24;}
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(24,r.height-24);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }

    /** Wraps command rows in dialogs instead of clipping a one-line FlowLayout. */
    static final class WrapLayout extends FlowLayout {
        WrapLayout() { super(FlowLayout.LEFT, 8, 8); }
        @Override public Dimension preferredLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                int width = parent.getWidth();
                if (width <= 0) width = 820;
                Insets insets = parent.getInsets();
                int available = Math.max(100, width - insets.left - insets.right - 16);
                int x = 0, y = 8, row = 0, max = 0;
                for (Component c : parent.getComponents()) if (c.isVisible()) {
                    Dimension d = c.getPreferredSize();
                    if (x > 0 && x + d.width > available) { y += row + 8; max = Math.max(max, x); x = 0; row = 0; }
                    x += d.width + 8;
                    row = Math.max(row, d.height);
                }
                return new Dimension(Math.min(width, Math.max(max, x) + insets.left + insets.right + 8),
                        y + row + insets.top + insets.bottom + 8);
            }
        }
        @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(100, 48); }
    }

    private static final class RibbonPage extends JPanel {
        private final Map<String, Group> groups = new LinkedHashMap<>();
        private final String tab;
        private boolean collapsed;
        private JComponent editor;
        RibbonPage(String tab) { this.tab=tab;setBackground(StudioVisuals.SURFACE); setLayout(null); }
        int order(String name){
            String[] order=switch(tab){
                case "Home"->new String[]{"Template","Files","Source","History","Clipboard","Insert","Arrange","Text"};
                case "Insert"->new String[]{"Text","Media","Shapes","Codes","Fields","Tables","Pages","Templates"};
                case "Data"->new String[]{"Records","Fields","Merge"};
                case "View"->new String[]{"Zoom","Panels","Pages","Preview","Filters"};
                default->new String[0];
            };
            for(int i=0;i<order.length;i++)if(order[i].equals(name))return i;return 20;
        }
        Group group(String name) {
            return groups.computeIfAbsent(name, key -> { Group g = new Group(key);int at=0;for(Component existing:getComponents())if(order(((Group)existing).title)<=order(name))at++;add(g,at);return g; });
        }
        void setCollapsed(boolean value) { collapsed = value; revalidate(); }
        private static final int GROUP_HEIGHT=124;
        int bodyHeight(int width){
            if(tab.equals("File"))return 0;
            if(editor!=null&&editor.getLayout() instanceof StudioRibbonBands bands){
                editor.setSize(Math.max(240,width-8),editor.getHeight());
                return bands.height(editor);
            }
            if(editor!=null)return editor.getClientProperty("studio.ribbon.height") instanceof Integer height?height:GROUP_HEIGHT;
            int available=Math.max(1,width-8),rows=1,x=0;
            for(Group group:fitGroups(available)){
                int wanted=Math.min(available,group.wantedWidth());
                if(x>0&&x+wanted>available){rows++;x=0;}
                x+=wanted;
            }
            return rows*GROUP_HEIGHT;
        }
        @Override public Dimension getPreferredSize() {
            return new Dimension(700, collapsed ? 0 : bodyHeight(getWidth()>0?getWidth():1100));
        }
        private List<Group> fitGroups(int available){
            List<Group> list = new ArrayList<>(groups.values());
            list.removeIf(group->!group.isVisible());
            list.sort(java.util.Comparator.comparingInt(g->order(g.title)));
            for (Group g : list) g.compact = g.body.getPreferredSize().height > 92;
            while (list.stream().mapToInt(Group::wantedWidth).sum() > available) {
                Group largest = null;
                for (Group g : list) if (!g.compact && !g.title.equals("History") && !g.title.equals("Insert") && !g.title.equals("Template")
                        && !(tab.equals("Insert")&&(g.title.equals("Codes")||g.title.equals("Text")))
                        && g.wantedWidth()>g.compactWidth() && (largest == null || g.wantedWidth() > largest.wantedWidth())) largest = g;
                if (largest == null) break;
                largest.compact = true;
            }
            return list;
        }
        @Override public void doLayout() {
            if(editor!=null){editor.setBounds(4,0,Math.max(0,getWidth()-8),collapsed?0:getHeight());return;}
            if (collapsed) { for (Group g : groups.values()) g.setBounds(0,0,0,0); return; }
            int available=Math.max(1,getWidth()-8),x=0,y=0;
            for (Group g : fitGroups(available)) {
                int width=Math.min(g.wantedWidth(),available);
                if(x>0&&x+width>available){x=0;y+=GROUP_HEIGHT;}
                g.setBounds(x,y,width,GROUP_HEIGHT);
                g.updatePresentation();
                x += width;
            }
        }
        JComponent dialogContent() {
            JPanel result = DeckersSwing.panel();
            result.setLayout(new BoxLayout(result,BoxLayout.Y_AXIS));
            List<Group> ordered=new ArrayList<>(groups.values());ordered.sort(java.util.Comparator.comparingInt(g->order(g.title)));
            for(Group g:ordered){
                if(!g.isVisible())continue;
                if(!g.isVisible())continue;
                JPanel row=DeckersSwing.panel();row.setLayout(new WrapLayout());
                JLabel caption=new JLabel(g.title);caption.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));row.add(caption);
                boolean fields=false;
                for(Component control:g.body.getComponents()){
                    if(control instanceof AbstractButton source){
                        JButton command=button(source.getText(),()->{
                            Window dialog=SwingUtilities.getWindowAncestor(result);if(dialog!=null)dialog.dispose();
                            SwingUtilities.invokeLater(()->source.doClick(0));
                        });
                        command.putClientProperty(StudioCommand.class,source.getClientProperty(StudioCommand.class));
                        command.setIcon(StudioVisuals.commandIcon(source,16));
                        command.setToolTipText(source.getToolTipText());
                        command.setEnabled(source.isEnabled());
                        row.add(command);
                    }else fields=true;
                }
                if(fields)row.add(button(g.title+" controls…",()->{
                    Window dialog=SwingUtilities.getWindowAncestor(result);if(dialog!=null)dialog.dispose();
                    SwingUtilities.invokeLater(()->showTask(RibbonPage.this,g.title,g.dialogBody()));
                }));
                result.add(row);
            }
            return result;
        }
    }

    private static final class Group extends JPanel {
        final String title;
        final JPanel body = DeckersSwing.panel();
        final JButton opener;
        boolean compact;
        Group(String title) {
            super(new BorderLayout(4, 4));
            this.title = title;
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 0, 1, StudioVisuals.BORDER),
                    BorderFactory.createEmptyBorder(8, 6, 6, 6)));
            body.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));
            body.setBackground(StudioVisuals.SURFACE);
            opener = button(title + "…", () -> showTask(this, title, dialogBody()));
            StudioVisuals.ribbonButton(opener);
            JLabel label = new JLabel(title);
            label.setText(title.toUpperCase(java.util.Locale.ROOT));
            label.setFont(StudioVisuals.font(Font.BOLD,12));
            label.setHorizontalAlignment(SwingConstants.LEFT);
            label.setForeground(StudioVisuals.INK);
            if(title.equals("Records")){
                setBorder(BorderFactory.createEmptyBorder(15,10,12,8));
                body.setLayout(new FlowLayout(FlowLayout.LEFT,13,10));
                body.setBackground(new Color(0xF6F0F7));
                body.setBorder(BorderFactory.createLineBorder(StudioVisuals.BORDER,1,true));
            }else add(label, BorderLayout.NORTH);
            add(body);
        }
        void addControl(Component c) {
            if(title.equals("Arrange")&&c instanceof AbstractButton b){String name=b.getText();if(name.contains("Forward")||name.contains("Backward")||name.contains("forward")||name.contains("backward")){body.add(c,name.toLowerCase(java.util.Locale.ROOT).contains("forward")?0:Math.min(1,body.getComponentCount()));revalidate();return;}}
            StudioVisuals.styleTree(c);
            if(title.equals("Records")&&c instanceof AbstractButton button)StudioVisuals.recordsButton(button);
            if(title.equals("Codes")&&c instanceof AbstractButton button){
                body.setLayout(new GridLayout(0,1,2,2));
                StudioWorkspaceShell.styleButton(button);
                button.setIcon(StudioVisuals.commandIcon(button,16));
                button.setHorizontalTextPosition(SwingConstants.RIGHT);button.setVerticalTextPosition(SwingConstants.CENTER);button.setIconTextGap(6);
                button.setPreferredSize(new Dimension(160,40));
            }
            if(title.equals("Text")&&c instanceof JPanel)body.add(c,0);else body.add(c);revalidate();
        }
        int wantedWidth() {
            return compact ? compactWidth() : body.getPreferredSize().width+padding();
        }
        int compactWidth(){return Math.max(108,opener.getPreferredSize().width+padding());}
        private int padding(){return getInsets().left+getInsets().right+2;}
        JComponent dialogBody() {
            body.setLayout(new WrapLayout());
            return body;
        }
        void updatePresentation() {
            remove(body); remove(opener);
            add(compact ? opener : body, BorderLayout.CENTER);
            doLayout();
        }
    }
}
