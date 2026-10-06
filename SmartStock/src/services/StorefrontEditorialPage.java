package services;

import java.util.Map;
import java.util.Set;

/** Public discovery pages that can be crawled without running JavaScript. */
final class StorefrontEditorialPage {
    private record Page(String title,String description,String heading,String summary) { }
    private static final Map<String,Page> PAGES=Map.of(
        "services",new Page("Explore Services | Deckers","Explore custom printing, 3D printing, apparel, signs, gifts, and more at Deckers in Guyana.","What can we make for you?","Find a starting point, then tell us what you have in mind. Our team will review your idea and help with the next steps."),
        "business",new Page("For Your Business | Deckers","Explore business cards, stationery, signs, apparel, and printing projects for your business at Deckers.","Make your brand feel like yours.","From the first business card to the sign outside, bring us the details you want customers to remember."),
        "about",new Page("About Deckers | See It. Imagine It. Make It.","Discover products, printing, personalization, and custom production at Deckers in Guyana.","See it. Imagine it. Make it.","Explore what our team has made and start with an idea of your own.")
    );
    private StorefrontEditorialPage() { }
    static Set<String> paths(){return PAGES.keySet();}
    static boolean has(String path){return PAGES.containsKey(path);}
    static String render(String template,String origin,String path){
        Page page=PAGES.get(path);
        if(page==null)throw new IllegalArgumentException("Unknown discovery page.");
        String url=origin+"/shop/"+path;
        String head="<title>"+page.title()+"</title>"+
            "<meta name=\"description\" content=\""+page.description()+"\"/>"+
            "<link rel=\"canonical\" href=\""+url+"\"/>"+
            "<meta property=\"og:type\" content=\"website\"/>"+
            "<meta property=\"og:title\" content=\""+page.title()+"\"/>"+
            "<meta property=\"og:description\" content=\""+page.description()+"\"/>"+
            "<meta property=\"og:url\" content=\""+url+"\"/>";
        String fallback="<main><h1>"+page.heading()+"</h1><p>"+page.summary()+"</p><p><a href=\"/shop/services/3d-printing\">3D Printing</a> · <a href=\"/shop/services/business-printing\">Business Printing</a> · <a href=\"/shop/\">Shop Deckers</a></p></main>";
        return template.replaceFirst("<title>[^<]*</title>","")
            .replaceFirst("<meta name=\"description\"[^>]*>","")
            .replace("</head>",head+"</head>")
            .replace("<div id=\"root\"></div>","<div id=\"root\">"+fallback+"</div>");
    }
}
