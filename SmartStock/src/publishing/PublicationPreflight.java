package publishing;

import java.util.*;
import java.util.regex.Pattern;

public final class PublicationPreflight {
  private PublicationPreflight() {}

  private static final Pattern FIELD = Pattern.compile("\\{\\{([^}]+)}}");

  public static List<String> missingMergeFields(Publication p) {
    Set<String> fields = new LinkedHashSet<>();
    for (var page : p.allPages())
      if (!page.reference)
        for (var object : page.objects) {
          String value =
              object.story.isBlank() ? object.text : p.stories.getOrDefault(object.story, "");
          var matcher = FIELD.matcher(value);
          while (matcher.find()) if (!matcher.group(1).equals("page")) fields.add(matcher.group(1));
        }
    List<String> errors = new ArrayList<>();
    for (String field : fields) {
      if (p.mergeRows.isEmpty()) errors.add("Merge field '" + field + "' needs data.");
      else
        for (int i = 0; i < p.mergeRows.size(); i++) {
          String value = p.mergeRows.get(i).get(field);
          if (value == null || value.isBlank()) {
            errors.add("Merge field '" + field + "' is missing on row " + (i + 1) + ".");
            break;
          }
        }
    }
    return errors;
  }

  public static void requireMergeData(Publication p) {
    p.validate();
    List<String> errors = missingMergeFields(p);
    if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
    for(var page:p.pages)if(!page.reference)for(var e:page.objects)if(e.type.equals("BARCODE")||e.type.equals("QR")){
      var rows=p.mergeRows.isEmpty()?List.<Map<String,String>>of(Map.of()):p.mergeRows;
      for(var row:rows)try{String value=e.text;for(var entry:row.entrySet())value=value.replace("{{"+entry.getKey()+"}}",entry.getValue());PublicationBarcode.encode(value,e.type.equals("QR")?"QR_CODE":e.barcodeFormat);}catch(Exception ex){throw new IllegalArgumentException("Invalid barcode: "+ex.getMessage(),ex);}
    }
  }
}
