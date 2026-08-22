package utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rewrites document image sources into URLs Swing's HTML renderer can display. */
public final class HtmlImageSourceResolver {
    private static final Pattern IMAGE_SOURCE = Pattern.compile(
            "(?is)(<img\\b[^>]*\\bsrc=['\"])([^'\"]+)(['\"][^>]*>)");

    private HtmlImageSourceResolver() { }

    public static String resolveForSwing(String html) {
        String value = html == null ? "" : html;
        Matcher matcher = IMAGE_SOURCE.matcher(value);
        StringBuffer resolved = new StringBuffer(value.length());
        while (matcher.find()) {
            String source = htmlDecode(matcher.group(2));
            String displayUrl = ImageCacheManager.resolveDisplayUrl(source);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(
                    matcher.group(1) + htmlAttribute(displayUrl) + matcher.group(3)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static String htmlDecode(String value) {
        return value == null ? "" : value.replace("&amp;", "&").replace("&#39;", "'")
                .replace("&quot;", "\"");
    }

    private static String htmlAttribute(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("'", "&#39;")
                .replace("\"", "&quot;");
    }
}
