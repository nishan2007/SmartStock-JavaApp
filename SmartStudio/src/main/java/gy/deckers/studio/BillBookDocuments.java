package gy.deckers.studio;

import publishing.Publication;
import java.nio.file.*;
import java.util.List;

/** Bill book originals remain separate from the card and label libraries. */
final class BillBookDocuments {
    static Path folder(){return Path.of(System.getProperty("user.home"),"template-library","bill-books");}
    static boolean numberingName(String name){return java.util.regex.Pattern.compile("(?i)(?<![a-z])(?:numbering|num)\\d*(?![a-z])").matcher(name).find();}
    static boolean numberingFile(Path file){return numberingName(folder().toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString());}
    static List<Path> files()throws java.io.IOException{return PublicationTemplateLibrary.files(List.of(folder())).stream().filter(p->!numberingFile(p)).toList();}
    static Publication create(){
        Publication p=new Publication();p.title="Bill book";p.template=true;
        p.width=139.7;p.height=215.9;p.displayUnit="IN";
        p.print.width=215.9;p.print.height=279.4;
        p.print.left=p.print.right=p.print.top=p.print.bottom=0;
        p.print.gapX=p.print.gapY=0;
        p.print.quantity=1;
        return p;
    }
}
