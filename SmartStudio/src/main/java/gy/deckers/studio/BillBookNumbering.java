package gy.deckers.studio;

import publishing.*;
import java.util.*;
import java.util.regex.Pattern;

/** Per-job numbering overlay; never removes artwork from the saved design. */
final class BillBookNumbering {
    private static final Pattern FIELD=Pattern.compile("\\{\\{([^}]+)}}");
    static Publication templateOnly(Publication source,String fields){
        Set<String> selected=new LinkedHashSet<>();
        for(String name:fields.split(","))if(!name.isBlank())selected.add(name.trim());
        if(selected.isEmpty())throw new IllegalArgumentException("Enter the number field names, separated by commas.");
        Publication result=source.copy();
        result.stories.replaceAll((name,text)->withoutNumbers(text,selected));
        for(var page:result.allPages())for(var element:page.objects){
            if(!element.type.equals("TEXT"))continue;
            String original=element.story.isBlank()?element.text:source.stories.getOrDefault(element.story,"");
            String text=withoutNumbers(original,selected);
            if(!text.equals(original)){
                element.text=withoutNumbers(element.text,selected);
                element.asset="";element.originalText="";element.sourceTable="";
            }
        }
        for(var row:result.mergeRows)selected.forEach(row::remove);
        result.mergeRows.removeIf(row->row.entrySet().stream().noneMatch(entry->!entry.getKey().equals("quantity")&&!entry.getValue().isBlank()));
        result.validate();return result;
    }
    private static String withoutNumbers(String text,Set<String> fields){
        return FIELD.matcher(text).replaceAll(match->fields.contains(match.group(1))?"":java.util.regex.Matcher.quoteReplacement(match.group()));
    }
    static Publication numbersOnly(Publication source,String fields){
        Set<String> selected=new LinkedHashSet<>();
        for(String name:fields.split(","))if(!name.isBlank())selected.add(name.trim());
        if(selected.isEmpty())throw new IllegalArgumentException("Enter the number field names, separated by commas.");
        Publication result=source.copy();int found=0;
        for(var page:result.allPages()){
            var keep=new ArrayList<Publication.Element>();
            for(var e:page.objects){
                if(!e.type.equals("TEXT"))continue;
                String value=e.story.isBlank()?e.text:result.stories.getOrDefault(e.story,"");
                var matcher=FIELD.matcher(value);var tokens=new ArrayList<String>();
                while(matcher.find())if(selected.contains(matcher.group(1)))tokens.add(matcher.group());
                if(tokens.isEmpty())continue;
                e.text=String.join(" ",tokens);e.story="";e.asset="";e.sourceTable="";
                e.originalText="";keep.add(e);found++;
            }
            page.objects=keep;page.risoPages.clear();page.risoExcluded.clear();
        }
        if(found==0)throw new IllegalArgumentException("No matching number fields. Add a Number field to the design, or enter its merge column name.");
        result.print.cropMarks=false;result.stories.clear();
        PublicationPreflight.requireMergeData(result);return result;
    }
}
