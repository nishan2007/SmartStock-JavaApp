package gy.deckers.studio;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** One flat directory of portable badge designs; assets travel inside each template. */
final class BadgeTemplateLibrary {
    private final Path folder;
    BadgeTemplateLibrary(Path folder) { this.folder=folder.toAbsolutePath().normalize(); }
    Path folder() { return folder; }
    List<Path> templates() throws IOException {
        Files.createDirectories(folder);
        try(var entries=Files.list(folder)) {
            return entries.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sstbadge"))
                    .sorted(Comparator.comparing(p->p.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
        }
    }
    Path fileFor(String name) {
        String clean=name.trim().replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]","-").replaceAll("[ .]+$","");
        if(clean.isEmpty())clean="Badge";
        if(clean.length()>100)clean=clean.substring(0,100);
        if(clean.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))clean="Badge-"+clean;
        return folder.resolve(clean+".sstbadge");
    }
    boolean contains(Path file) { return file!=null&&folder.equals(file.toAbsolutePath().normalize().getParent()); }
    static String fingerprint(Path file)throws IOException {
        try(var input=Files.newInputStream(file)) {
            try {var hash=java.security.MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1)hash.update(bytes,0,count);return HexFormat.of().formatHex(hash.digest());}
            catch(java.security.NoSuchAlgorithmException ex){throw new AssertionError(ex);}
        }
    }
}
