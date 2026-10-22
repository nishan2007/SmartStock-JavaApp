package ui.screens.customorders;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Computes a whole-set replacement before the live cart is changed. */
final class CustomOrderSetCart {
    static <T> List<T> replace(List<T> original, List<T> replacement, int selected, String setId, Function<T,String> identity) {
        List<T> result = new ArrayList<>(original);
        int insertion = selected < 0 ? result.size() : selected;
        if (setId != null) {
            for (int i = 0; i < result.size(); i++) if (setId.equals(identity.apply(result.get(i)))) { insertion = i; break; }
            result.removeIf(line -> setId.equals(identity.apply(line)));
        } else if (selected >= 0) result.remove(selected);
        result.addAll(Math.min(insertion, result.size()), replacement);
        return result;
    }
    static <T> List<T> remove(List<T> original, int selected, Function<T,String> identity) {
        return replace(original, List.of(), selected, identity.apply(original.get(selected)), identity);
    }
}
