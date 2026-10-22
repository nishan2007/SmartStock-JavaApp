package services;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Shared catalog matching for item details, inventory rows, and scanner identifiers. */
public final class CustomCatalogSearch {
    private CustomCatalogSearch() { }

    public static boolean matches(String text, List<String> identifiers, String query) {
        if (query == null || query.isBlank()) return true;
        List<String> candidates = BarcodeNormalizer.lookupCandidates(query);
        if (identifiers != null && identifiers.stream().anyMatch(id -> {
            String normalized = BarcodeNormalizer.normalize(id);
            return !normalized.isEmpty() && candidates.contains(normalized);
        })) return true;
        StringBuilder searchable = new StringBuilder(text == null ? "" : text);
        if (identifiers != null) identifiers.forEach(id -> searchable.append(' ').append(id == null ? "" : id));
        return ProductSearchHelper.textMatches(searchable.toString(), query);
    }

    public static boolean matchesItem(LanCustomOrderCatalogAdminService.Item item, String query) {
        if (matches(fields(item.name(),item.description(),item.size(),item.color(),item.brand(),item.categoryName(),item.itemType(),item.pricingType(),Long.toString(item.itemId())),
                identifiers(item.sku(),item.barcode(),item.extraBarcodes()),query)) return true;
        return item.variants()!=null && item.variants().stream().anyMatch(variant -> matches(
                fields(item.name(),item.description(),item.size(),item.color(),item.brand(),item.categoryName(),item.itemType(),variant.name(),variant.size(),variant.color(),variant.brand()),
                Stream.concat(identifiers(item.sku(),item.barcode(),item.extraBarcodes()).stream(), identifiers(variant.sku(),variant.barcode(),variant.extraBarcodes()).stream()).toList(),query));
    }

    private static List<String> identifiers(String sku,String barcode,List<String> extras) {
        List<String> values=new ArrayList<>();values.add(sku);values.add(barcode);
        if(extras!=null)values.addAll(extras);
        return values;
    }
    private static String fields(String... values) {
        return Stream.of(values).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining(" "));
    }
}
