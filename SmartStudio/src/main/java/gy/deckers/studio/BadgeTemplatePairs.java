package gy.deckers.studio;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Exports combined designs without changing source files or shared-library metadata. */
public final class BadgeTemplatePairs {
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: BadgeTemplatePairs source-folder new-output-folder");
        Path source=Path.of(args[0]).toRealPath(),output=Path.of(args[1]).toAbsolutePath().normalize();
        if(Files.exists(output))throw new IllegalArgumentException("Choose a new output folder.");
        Map<String,List<Path>> names=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try(var files=Files.list(source)) {
            for(Path file:files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".sstbadge")).sorted().toList()) {
                String name=JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("name").getAsString();
                names.computeIfAbsent(name,k->new ArrayList<>()).add(file);
            }
        }
        Files.createDirectories(output);var library=new BadgeTemplateLibrary(output);
        var report=new StringBuilder("Template\tResult\tFront source\tBack source\n");int count=0;
        for(var entry:names.entrySet()) {
            String frontName=entry.getKey();if(!frontName.matches("(?i).* - Front$"))continue;
            String base=frontName.replaceFirst("(?i) - Front$","");
            var backs=names.get(base+" - Back");
            if(backs==null){report.append(base).append("\tNo matching Back\n");continue;}
            Path front=unique(entry.getValue()),back=unique(backs);
            if(front==null||back==null){report.append(base).append("\tDifferent duplicate designs; review required\n");continue;}
            var template=BadgeTemplate.load(front);
            if(!template.backElements.isEmpty()){report.append(base).append("\tAlready has Back; review required\n");continue;}
            template.mergeBack(BadgeTemplate.load(back));template.name=base;template.smartDrive=null;
            Path target=library.fileFor(base);if(Files.exists(target))throw new IllegalArgumentException("Duplicate output name: "+base);
            template.save(target);var loaded=BadgeTemplate.load(target);
            if(loaded.elements.size()!=template.elements.size()||loaded.backElements.size()!=template.backElements.size())throw new IllegalStateException("Merge verification failed");
            report.append(base).append("\tMerged\t").append(front).append('\t').append(back).append('\n');count++;
        }
        Files.writeString(output.resolve("merge-report.tsv"),report);
        System.out.println(count+" combined templates exported to "+output);
    }
    private static Path unique(List<Path> paths)throws Exception {
        String expected=null;Path result=null;
        for(Path path:paths) {
            var design=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            design.remove("previewPng");design.remove("smartDrive");
            for(var item:design.getAsJsonArray("elements"))item.getAsJsonObject().remove("photoId");
            String value=design.toString();
            if(expected!=null&&!expected.equals(value))return null;
            expected=value;result=path;
        }
        return result;
    }
}
