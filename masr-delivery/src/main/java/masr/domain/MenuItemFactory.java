package masr.domain;

import masr.common.Money;
import masr.exception.InvalidInputException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Factory with a registry keyed by the data file's "type" field. All type-decision logic lives in
 * this one class; a new item kind registers one creator and no other code changes.
 */
public final class MenuItemFactory {
    public record ItemSpec(String restaurantId, String itemId, String type, String name, Money price,
                           String category, int prepMinutes, boolean available, String extra) { }

    @FunctionalInterface
    public interface Creator {
        MenuItem create(ItemSpec spec, Function<String, MenuItem> siblings, BigDecimal stock);
    }

    private final Map<String, Creator> creators = new TreeMap<>();

    public MenuItemFactory register(String type, Creator c) {
        creators.put(type.trim().toUpperCase(Locale.ROOT), c);
        return this;
    }

    public static MenuItemFactory withDefaults() {
        return new MenuItemFactory()
                .register("STANDARD", (s, sib, st) -> new StandardItem(s.restaurantId(), s.itemId(), s.name(),
                        s.price(), s.category(), s.prepMinutes(), s.available(), st))
                .register("WEIGHTED", (s, sib, st) -> new WeightedItem(s.restaurantId(), s.itemId(), s.name(),
                        s.price(), s.category(), s.prepMinutes(), s.available(), st))
                .register("COMBO", MenuItemFactory::combo);
    }

    public Set<String> types() { return Collections.unmodifiableSet(creators.keySet()); }

    public MenuItem create(ItemSpec spec, Function<String, MenuItem> siblings, BigDecimal stock) {
        Creator c = creators.get(spec.type() == null ? "" : spec.type().trim().toUpperCase(Locale.ROOT));
        if (c == null) throw new InvalidInputException("Unknown item type '" + spec.type() + "'. Known: " + types());
        return c.create(spec, siblings, stock);
    }

    /** extra = "ID1+ID2@10": component item ids, then the bundle discount percent. */
    private static MenuItem combo(ItemSpec s, Function<String, MenuItem> siblings, BigDecimal stock) {
        String extra = s.extra() == null ? "" : s.extra();
        int at = extra.lastIndexOf('@');
        if (at < 0) throw new InvalidInputException("Combo needs 'ID1+ID2@discountPercent' as extra data");
        BigDecimal pct;
        try { pct = new BigDecimal(extra.substring(at + 1).trim()); }
        catch (NumberFormatException e) { throw new InvalidInputException("Combo discount is not a number"); }
        List<MenuItem> parts = new ArrayList<>();
        for (String id : extra.substring(0, at).split("\\+")) {
            MenuItem m = siblings.apply(id.trim().toUpperCase(Locale.ROOT));
            if (m == null) throw new InvalidInputException("Combo component '" + id.trim() + "' is not on this menu");
            parts.add(m);
        }
        return new ComboItem(s.restaurantId(), s.itemId(), s.name(), parts, pct, s.category(), s.prepMinutes(),
                s.available(), stock);
    }
}
