package services;

import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.UUID;

/** Public project metadata and a useful no-script document for search and sharing. */
final class StorefrontProjectPage {
    private StorefrontProjectPage() { }

    static String render(String template,JsonObject project,String origin,int storeId,UUID id){
        String url=StorefrontProjectQr.url(origin,storeId,id),title=StorefrontService.text(project,"title"),
            summary=StorefrontService.text(project,"summary"),category=StorefrontService.text(project,"category");
        String image=origin+"/shop/image?storeId="+storeId+"&kind=project&projectId="+id;
        String head="<title>"+escape(title)+" | Made at Deckers</title>"+
            "<meta name=\"description\" content=\""+escape(summary)+"\"/>"+
            "<link rel=\"canonical\" href=\""+escape(url)+"\"/>"+
            "<meta property=\"og:type\" content=\"article\"/>"+
            "<meta property=\"og:title\" content=\""+escape(title)+" | Made at Deckers\"/>"+
            "<meta property=\"og:description\" content=\""+escape(summary)+"\"/>"+
            "<meta property=\"og:url\" content=\""+escape(url)+"\"/>"+
            "<meta property=\"og:image\" content=\""+escape(image)+"\"/>";
        JsonObject structured=new JsonObject();structured.addProperty("@context","https://schema.org");structured.addProperty("@type","CreativeWork");
        structured.addProperty("name",title);structured.addProperty("description",summary);structured.addProperty("genre",category);
        structured.addProperty("url",url);structured.addProperty("image",image);
        head+="<script type=\"application/ld+json\">"+structured.toString().replace("<","\\u003c")+"</script>";
        String fallback="<article class=\"project-index\"><p>Made at Deckers / "+escape(category)+"</p><h1>"+escape(title)+"</h1><p>"+escape(summary)+"</p>"+
            "<p><a href=\"/shop/#create/project/"+id+"\">Make something like this</a></p></article>";
        return template.replaceFirst("<title>[^<]*</title>","")
            .replaceFirst("<meta name=\"description\"[^>]*>","")
            .replace("</head>",head+"</head>")
            .replace("<div id=\"root\"></div>","<div id=\"root\">"+fallback+"</div>");
    }

    static String sitemap(String origin,Collection<String> projectUrls){
        StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        xml.append("<url><loc>").append(escape(origin+"/shop/")).append("</loc></url>");
        for(String url:projectUrls)xml.append("<url><loc>").append(escape(url)).append("</loc></url>");
        return xml.append("</urlset>").toString();
    }

    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
