package services;

import com.google.gson.JsonObject;

/** Search metadata for a product that is currently published by one store. */
final class StorefrontProductPage {
    private StorefrontProductPage() { }
    static String url(String origin,int storeId,int id){return origin+"/shop/products/"+storeId+"/"+id;}
    static String render(String template,JsonObject product,JsonObject settings,String origin,int storeId,int id){
        String name=StorefrontService.text(product,"name"),description=StorefrontService.text(product,"description");
        if(description.isBlank())description=name+" at Deckers. Check current availability and order for store pickup.";
        String currency=StorefrontService.text(settings,"currency");
        if(currency.isBlank())currency="GYD";
        String price=product.get("price").getAsBigDecimal().toPlainString();
        String address=url(origin,storeId,id),title=name+" | Deckers";
        String image=StorefrontService.text(product,"image").isBlank()?"":origin+"/shop/image?storeId="+storeId+"&kind=product&id="+id;
        String head="<title>"+escape(title)+"</title>"+
            "<meta name=\"description\" content=\""+escape(description)+"\"/>"+
            "<link rel=\"canonical\" href=\""+escape(address)+"\"/>"+
            "<meta property=\"og:type\" content=\"product\"/>"+
            "<meta property=\"og:title\" content=\""+escape(title)+"\"/>"+
            "<meta property=\"og:description\" content=\""+escape(description)+"\"/>"+
            "<meta property=\"og:url\" content=\""+escape(address)+"\"/>"+
            (image.isBlank()?"":"<meta property=\"og:image\" content=\""+escape(image)+"\"/>");
        JsonObject structured=new JsonObject();structured.addProperty("@context","https://schema.org");structured.addProperty("@type","Product");
        structured.addProperty("name",name);structured.addProperty("description",description);structured.addProperty("url",address);
        if(!StorefrontService.text(product,"sku").isBlank())structured.addProperty("sku",StorefrontService.text(product,"sku"));
        if(!image.isBlank())structured.addProperty("image",image);
        JsonObject offer=new JsonObject();offer.addProperty("@type","Offer");offer.addProperty("priceCurrency",currency);offer.addProperty("price",price);
        offer.addProperty("availability","https://schema.org/"+(Boolean.TRUE.equals(product.get("canOrder").getAsBoolean())?"InStock":"OutOfStock"));
        offer.addProperty("url",address);structured.add("offers",offer);
        head+="<script type=\"application/ld+json\">"+structured.toString().replace("<","\\u003c")+"</script>";
        String fallback="<article class=\"product-index\"><h1>"+escape(name)+"</h1><p>"+escape(description)+"</p><p>"+escape(currency)+" "+escape(price)+" · "+escape(StorefrontService.text(product,"availability"))+"</p>"+
            "<p><a href=\"/shop/#shop\">Browse products</a></p></article>";
        return template.replaceFirst("<title>[^<]*</title>","")
            .replaceFirst("<meta name=\"description\"[^>]*>","")
            .replace("</head>",head+"</head>")
            .replace("<div id=\"root\"></div>","<div id=\"root\">"+fallback+"</div>");
    }
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
