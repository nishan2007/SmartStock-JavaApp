package services;

import java.util.Map;
import java.util.Set;
import static java.util.Map.entry;

/** Indexable metadata and a useful HTML fallback for public service URLs. */
final class StorefrontServicePage {
    private record Page(String name, String description, String guidance) { }
    private static final Map<String,Page> PAGES=Map.ofEntries(
        entry("3d-printing",new Page("3D Printing","Upload a model or describe your idea. Our team will review the details and follow up with a quote.","Upload an STL, OBJ, or 3MF model, or describe what you would like to make.")),
        entry("custom-printing",new Page("Custom Printing","Tell us what you need printed and how you want it to look. We will help choose a suitable process and prepare a quote.","Share your artwork, size, quantity, and preferred finish.")),
        entry("custom-apparel",new Page("Custom Apparel","Bring your artwork, logo, or concept to a wearable project. Share the garment, quantity, sizes, and finish you have in mind.","Tell us about your garment, sizes, artwork, and quantity.")),
        entry("embroidery",new Page("Embroidery","Tell us about the garment, artwork, placement, and quantity. Our team will review the stitching requirements before quoting.","Share the garment, placement, artwork, and quantity you have in mind.")),
        entry("signs-banners",new Page("Signs & Banners","Share where your sign or banner will be used, its size, quantity, and artwork. We will recommend a suitable production approach.","Tell us the size, location, quantity, and artwork for your sign or banner.")),
        entry("business-printing",new Page("Business Printing","Tell us about the printed materials your business needs. Our team will review format, quantity, artwork, and timing.","Share the format, artwork, quantity, and timing for your business materials.")),
        entry("personalized-gifts",new Page("Personalized Gifts","Share the occasion and the personal details you would like to add. We will help turn your idea into a thoughtful project.","Tell us about the occasion, your idea, and the personal details to include.")),
        entry("business-cards",new Page("Business Cards","Tell us about your business cards, including size, quantity, artwork, and finish. Our team will review the details and prepare a quote.","Share your card artwork, quantity, size, and preferred finish.")),
        entry("banner-printing",new Page("Banner Printing","Share the banner size, where it will be displayed, quantity, and artwork. We will review production options and prepare a quote.","Tell us the banner dimensions, display setting, quantity, and artwork.")),
        entry("custom-t-shirts",new Page("Custom T-Shirts","Tell us about the shirts, sizes, colors, quantity, and artwork you have in mind. Our team will review a suitable production method.","Share the shirt sizes, colors, quantity, and artwork.")),
        entry("laser-engraving",new Page("Laser Engraving","Describe the item, material, artwork, quantity, and placement you have in mind. Our team will review whether it is suitable for engraving.","Tell us the item material, artwork, placement, and quantity.")),
        entry("stationery",new Page("Stationery","Tell us which stationery pieces you need, how many, and what artwork you have. We will review the format and prepare a quote.","Share the stationery format, quantity, and artwork you need.")),
        entry("large-format-printing",new Page("Large Format Printing","Share the dimensions, display setting, artwork, and quantity for your large format project. Our team will review suitable materials and production options.","Tell us the dimensions, display setting, artwork, and quantity.")),
        entry("signs",new Page("Signs","Tell us where the sign will be used, its dimensions, artwork, and quantity. We will review suitable materials and prepare a quote.","Share the sign dimensions, location, artwork, and quantity."))
    );
    private StorefrontServicePage() { }
    static Set<String> slugs(){return PAGES.keySet();}
    static boolean has(String slug){return PAGES.containsKey(slug);}
    static String name(String slug){return PAGES.containsKey(slug)?PAGES.get(slug).name():"";}
    static String slugForName(String name){
        for(var page:PAGES.entrySet())if(page.getValue().name().equalsIgnoreCase(name))return page.getKey();
        return "";
    }
    static String render(String template,String origin,String slug){
        Page page=PAGES.get(slug);
        if(page==null)throw new IllegalArgumentException("Unknown service.");
        String url=origin+"/shop/services/"+slug;
        String title=page.name()+" in Guyana | Deckers";
        String description=page.description();
        String head="<title>"+title+"</title>"+
            "<meta name=\"description\" content=\""+description+"\"/>"+
            "<link rel=\"canonical\" href=\""+url+"\"/>"+
            "<meta property=\"og:type\" content=\"website\"/>"+
            "<meta property=\"og:title\" content=\""+title+"\"/>"+
            "<meta property=\"og:description\" content=\""+description+"\"/>"+
            "<meta property=\"og:url\" content=\""+url+"\"/>"+
            "<script type=\"application/ld+json\">{\"@context\":\"https://schema.org\",\"@type\":\"Service\",\"name\":\""+page.name()+"\",\"areaServed\":\"Guyana\",\"url\":\""+url+"\"}</script>";
        String fallback="<main><h1>"+page.name()+" at Deckers</h1><p>"+page.guidance()+" Our team will review your request and follow up with a quote.</p><p><a href=\"/shop/#create/"+page.name().replace(" ","%20").replace("&","%26")+"\">Start a project request</a></p></main>";
        return template.replaceFirst("<title>[^<]*</title>","")
            .replaceFirst("<meta name=\"description\"[^>]*>","")
            .replace("</head>",head+"</head>")
            .replace("<div id=\"root\"></div>","<div id=\"root\">"+fallback+"</div>");
    }
}
