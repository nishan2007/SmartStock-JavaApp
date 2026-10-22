package services;

import com.google.gson.JsonObject;

/** One service category per custom item; legacy flags remain wire-compatible. */
public enum CustomItemCategory {
    NONE("None / Other",""),SHIRT("Shirts","isShirt"),SUBLIMATION("Sublimation","isSublimation"),
    BUSINESS_CARDS("Business Cards","isBusinessCards"),LARGE_FORMAT("Large Format Printing","isLargeFormatPrinting"),LASER("Laser","isLaser");
    private final String label,field;
    CustomItemCategory(String label,String field){this.label=label;this.field=field;}
    @Override public String toString(){return label;}
    public static CustomItemCategory from(JsonObject item){
        for(var category:values())if(!category.field.isEmpty()&&item.has(category.field)&&!item.get(category.field).isJsonNull()&&item.get(category.field).getAsBoolean())return category;
        return NONE;
    }
    public static CustomItemCategory parse(Object label){
        for(var category:values())if(category==label||category.label.equals(String.valueOf(label)))return category;
        throw new IllegalArgumentException("Choose a valid custom item category.");
    }
    public void apply(JsonObject item){for(var category:values())if(!category.field.isEmpty())item.addProperty(category.field,category==this);}
    public static void requireSingle(Boolean... flags){int count=0;for(Boolean flag:flags)if(Boolean.TRUE.equals(flag))count++;if(count>1)throw new IllegalArgumentException("Choose only one custom item category.");}
}
