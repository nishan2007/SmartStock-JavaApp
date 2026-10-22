package gy.deckers.studio;

import publishing.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Local reusable card/label designs; merge records never belong in this library. */
final class PublicationTemplateLibrary {
    private PublicationTemplateLibrary() {}
    static Path folder(boolean labels){return Path.of(System.getProperty("user.home"),"template-library",labels?"labels":"business-cards");}
    static List<Path> files(boolean labels)throws java.io.IOException{
        String home=System.getProperty("smartstudio.register.home",System.getProperty("user.home"));
        List<Path> all=files(List.of(folder(labels),Path.of(home,"Documents","SmartStudio",labels?"Imported Labels":"Imported Business Cards")));
        Set<String> upgraded=new HashSet<>();for(Path file:all)if(file.startsWith(folder(labels).toAbsolutePath().normalize()))upgraded.add(name(file).toLowerCase(Locale.ROOT));
        return all.stream().filter(file->file.startsWith(folder(labels).toAbsolutePath().normalize())||!upgraded.contains(name(file).toLowerCase(Locale.ROOT))).toList();
    }
    static List<Path> files(List<Path> roots)throws java.io.IOException{
        Set<Path> found=new LinkedHashSet<>();
        for(Path root:roots)if(Files.isDirectory(root))try(var paths=Files.walk(root,5)){
            paths.filter(Files::isRegularFile).filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".sspub")).limit(10001).forEach(p->found.add(p.toAbsolutePath().normalize()));
        }
        if(found.size()>10000)throw new java.io.IOException("Template library exceeds 10000 files.");
        return found.stream().sorted(Comparator.comparing(p->p.getFileName().toString(),String.CASE_INSENSITIVE_ORDER)).toList();
    }
    static String name(Path file){return file.getFileName().toString().replaceFirst("(?i)(\\.pub)?\\.sspub$","");}
    static String missingFonts(Publication p){
        Set<String> installed=new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        Set<String> missing=new TreeSet<>();
        for(var page:p.pages)for(var e:page.objects)if(e.type.equals("TEXT")&&!e.font.isBlank()&&!PublicationFonts.available(e.font))missing.add(e.font);
        return String.join(", ",missing);
    }
    static BufferedImage preview(Publication p,int page){
        double scale=Math.min(650/p.width,440/p.height);BufferedImage image=new BufferedImage(Math.max(1,(int)Math.ceil(p.width*scale)),Math.max(1,(int)Math.ceil(p.height*scale)),BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();try{g.scale(scale,scale);new PublicationRenderer(p,1200).page(g,page,Map.of());}finally{g.dispose();}return image;
    }
    static BadgeTemplatePicker.Choice choose(Component owner,boolean labels)throws Exception{return choose(owner,labels,null);}
    private static List<BadgeTemplatePicker.Choice> choices(boolean labels,SharedPublicationLibrary shared)throws Exception{
        return choices(labels,shared,files(labels));
    }
    static List<BadgeTemplatePicker.Choice> choices(boolean labels,SharedPublicationLibrary shared,List<Path> local)throws Exception{
        List<BadgeTemplatePicker.Choice> choices=new ArrayList<>();
        choices.add(new BadgeTemplatePicker.Choice("Continue current design",BadgeTemplatePicker.Kind.CURRENT,null));
        choices.add(new BadgeTemplatePicker.Choice(labels?"New label":"New business card",BadgeTemplatePicker.Kind.BLANK,null));
        choices.add(new BadgeTemplatePicker.Choice("Import template file…",BadgeTemplatePicker.Kind.IMPORT,null));
        List<Path> files=new ArrayList<>(shared==null?local:shared.files());
        if(shared!=null&&(!shared.online||files.isEmpty()))for(Path file:local)if(!files.contains(file))files.add(file);
        Map<String,Long> counts=new HashMap<>();
        for(Path file:files)counts.merge(choiceLabel(shared,file),1L,Long::sum);
        for(Path file:files){String label=choiceLabel(shared,file);if(counts.get(label)>1)label+=" · "+file.getParent().getFileName();choices.add(new BadgeTemplatePicker.Choice(label,BadgeTemplatePicker.Kind.FILE,file));}
        return choices;
    }
    static List<BadgeTemplatePicker.Choice> billBookChoices(SharedPublicationLibrary shared)throws Exception{
        var choices=choices(false,shared,BillBookDocuments.files());
        choices.replaceAll(c->c.kind()==BadgeTemplatePicker.Kind.BLANK?new BadgeTemplatePicker.Choice("New bill book",c.kind(),c.file()):c);
        choices.removeIf(c->c.kind()==BadgeTemplatePicker.Kind.FILE&&BillBookDocuments.numberingName(c.label()));
        return choices;
    }
    private static String choiceLabel(SharedPublicationLibrary shared,Path file){return shared==null?name(file):shared.contains(file)?shared.label(file):name(file)+" · Local on this computer";}
    private static final class JsonObjectHolder {
        static void assign(Component owner,SharedPublicationLibrary shared,Path file){var body=shared.stores.assignment(owner,file);if(body==null)return;new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{shared.assign(body);return null;}protected void done(){try{get();}catch(Exception ex){JOptionPane.showMessageDialog(owner,ex.getCause()==null?ex.getMessage():ex.getCause().getMessage(),"Template stores",JOptionPane.ERROR_MESSAGE);}}}.execute();}
    }
    static BadgeTemplatePicker.Choice choose(Component owner,boolean labels,SharedPublicationLibrary shared)throws Exception{
        return choose(owner,labels,shared,false);
    }
    static BadgeTemplatePicker.Choice choose(Component owner,boolean labels,SharedPublicationLibrary shared,boolean banners)throws Exception{
        return choose(owner,labels,shared,banners,false);
    }
    static BadgeTemplatePicker.Choice choose(Component owner,boolean labels,SharedPublicationLibrary shared,boolean banners,boolean billBooks)throws Exception{
        List<BadgeTemplatePicker.Choice> choices=billBooks?billBookChoices(shared):banners?choices(false,null,BannerDocuments.files()):choices(labels,shared);
        if(billBooks)choices.set(1,new BadgeTemplatePicker.Choice("New bill book",BadgeTemplatePicker.Kind.BLANK,null));
        if(banners)choices.set(1,new BadgeTemplatePicker.Choice("New banner",BadgeTemplatePicker.Kind.BLANK,null));
        SheetPreview preview=new SheetPreview();preview.setPreferredSize(new Dimension(460,360));JLabel detail=new JLabel(" ");
        JComboBox<Integer> pages=new JComboBox<>();JPanel right=new JPanel(new BorderLayout(4,4));right.add(preview);JPanel bottom=new JPanel(new BorderLayout());bottom.add(detail);bottom.add(pages,BorderLayout.EAST);right.add(bottom,BorderLayout.SOUTH);
        int[] generation={0};Publication[] loaded={null};JLabel libraryState=new JLabel(shared==null?"Local templates":shared.status);right.add(libraryState,BorderLayout.NORTH);
        pages.addActionListener(e->{if(loaded[0]!=null&&pages.getSelectedIndex()>=0)preview.setImage(preview(loaded[0],pages.getSelectedIndex()));});
        BadgeTemplatePicker picker=new BadgeTemplatePicker(choices,right,choice->{
            int request=++generation[0];loaded[0]=null;pages.removeAllItems();preview.setImage(null);
            if(choice==null||choice.file()==null){detail.setText(choice==null?"No selection":choice.label());return;}
            detail.setText("Loading preview…");
            new SwingWorker<Publication,Void>(){
                protected Publication doInBackground()throws Exception{if(banners)return PublicationLargeFiles.read(choice.file(),PublicationLargeFiles.cache());if(Files.size(choice.file())>PublicationFiles.MAX_BYTES)throw new java.io.IOException("Template exceeds 48 MiB.");return PublicationFiles.read(Files.readAllBytes(choice.file()));}
                protected void done(){if(request!=generation[0])return;try{Publication p=get();loaded[0]=p;long text=p.pages.stream().flatMap(pg->pg.objects.stream()).filter(o->o.type.equals("TEXT")).count();detail.setText(String.format(Locale.ROOT,"<html>%.2f × %.2f mm · %d pages · %d text fields%s</html>",p.width,p.height,p.pages.size(),text,(text==0?"<br>Picture artwork; its lettering is not separately editable.":"")+(missingFonts(p).isBlank()?"":"<br>Missing source fonts: "+missingFonts(p)+". Appearance differs.")));for(int i=0;i<p.pages.size();i++)pages.addItem(i+1);}catch(Exception ex){detail.setText("Cannot open this template.");}}
            }.execute();
        });
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(owner),billBooks?"Bill book templates":banners?"Banner templates":labels?"Label templates":"Business-card templates",Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);dialog.add(picker);JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));JButton open=new JButton("Open selected"),cancel=new JButton("Cancel");ui.design.DeckersSwing.styleUtilityButton(open,ui.design.DeckersPalette.PURPLE);ui.design.DeckersSwing.styleUtilityButton(cancel,ui.design.DeckersPalette.PURPLE);actions.add(cancel);actions.add(open);dialog.add(actions,BorderLayout.SOUTH);
        if(shared!=null){JPanel storeTools=new JPanel(new FlowLayout(FlowLayout.LEFT));storeTools.add(new JLabel("Store folder"));storeTools.add(shared.stores.selector(()->{try{picker.replaceChoices(billBooks?billBookChoices(shared):choices(labels,shared));}catch(Exception ignored){}}));JButton assign=new JButton("Store ownership / sharing…");ui.design.DeckersSwing.styleUtilityButton(assign,ui.design.DeckersPalette.PURPLE);assign.addActionListener(e->{var selected=picker.selection();if(selected==null||selected.file()==null)return;JsonObjectHolder.assign(owner,shared,selected.file());});storeTools.add(assign);dialog.add(storeTools,BorderLayout.NORTH);}
        BadgeTemplatePicker.Choice[] result={null};open.addActionListener(e->{if(picker.selection()!=null){result[0]=picker.selection();dialog.dispose();}});cancel.addActionListener(e->dialog.dispose());
        if(shared!=null){
            JButton delete=StudioWorkspaceShell.button("Delete selected…",()->{
                var selected=picker.selection();
                if(selected==null||selected.file()==null||!shared.contains(selected.file()))return;
                if(JOptionPane.showConfirmDialog(dialog,"Delete “"+selected.label()+"” from the shared library?\nThis removes it for every store where it is shared. Saved designs already open remain unchanged.","Delete template",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)return;
                open.setEnabled(false);
                new SwingWorker<Void,Void>(){
                    protected Void doInBackground()throws Exception{shared.delete(selected.file());return null;}
                    protected void done(){try{get();picker.replaceChoices(billBooks?billBookChoices(shared):choices(labels,shared));}catch(Exception ex){JOptionPane.showMessageDialog(dialog,ex.getCause()==null?ex.getMessage():ex.getCause().getMessage(),"Template deletion",JOptionPane.ERROR_MESSAGE);}finally{open.setEnabled(picker.selection()!=null);}}
                }.execute();
            });actions.add(delete,0);
        }
        JButton create=StudioWorkspaceShell.button(billBooks?"Create new bill book template":banners?"Create new banner template":labels?"Create new label template":"Create new business card template",()->{
            result[0]=new BadgeTemplatePicker.Choice(billBooks?"New bill book":banners?"New banner":labels?"New label":"New business card",BadgeTemplatePicker.Kind.BLANK,null);dialog.dispose();
        });actions.add(create,0);
        picker.addPropertyChangeListener("selectionAvailable",e->open.setEnabled(picker.selection()!=null));dialog.getRootPane().setDefaultButton(open);dialog.setSize(900,580);dialog.setMinimumSize(new Dimension(700,440));dialog.setLocationRelativeTo(owner);picker.focusSearch();
        String[] previous={choices.toString()};javax.swing.Timer updates=new javax.swing.Timer(2000,e->{if(shared==null)return;libraryState.setText(shared.status+(shared.migrationProgress.isBlank()?"":" • "+shared.migrationProgress));try{var latest=billBooks?billBookChoices(shared):choices(labels,shared);String signature=latest.toString();if(!signature.equals(previous[0])){previous[0]=signature;picker.replaceChoices(latest);}}catch(Exception ignored){}});
        if(shared!=null)updates.start();try{dialog.setVisible(true);}finally{updates.stop();}generation[0]++;return result[0];
    }
}
