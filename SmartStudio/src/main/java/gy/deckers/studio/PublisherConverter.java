package gy.deckers.studio;

import java.nio.file.*;
import java.util.*;
import java.util.prefs.Preferences;

/** Finds a local converter and remembers an explicitly selected executable. */
final class PublisherConverter {
    private static final Preferences SETTINGS=Preferences.userNodeForPackage(PublisherConverter.class);
    private PublisherConverter() {}
    static Path find() {
        List<Path> candidates=new ArrayList<>();
        String saved=SETTINGS.get("publisherConverter", "");
        if(!saved.isBlank())try{candidates.add(Path.of(saved));}catch(InvalidPathException ignored){}
        for(String variable:List.of("ProgramFiles","ProgramFiles(x86)")){
            String root=System.getenv(variable);
            if(root!=null)for(String name:List.of("soffice.com","soffice.exe"))candidates.add(Path.of(root,"LibreOffice","program",name));
        }
        String home=System.getProperty("smartstudio.register.home",System.getProperty("user.home"));
        candidates.add(Path.of(home,"Tools","LibreOffice-import","extracted","program","soffice.com"));
        candidates.add(Path.of("/Applications/LibreOffice.app/Contents/MacOS/soffice"));
        String path=System.getenv("PATH");
        if(path!=null)for(String part:path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
            if(!part.isBlank())try{for(String name:List.of("soffice.com","soffice.exe","soffice"))candidates.add(Path.of(part,name));}catch(InvalidPathException ignored){}
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }
    static void remember(Path executable){SETTINGS.put("publisherConverter",executable.toAbsolutePath().toString());}
}
