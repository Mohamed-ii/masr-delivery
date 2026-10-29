package masr.domain;

import masr.common.Money;
import masr.exception.InvalidInputException;

import java.math.BigDecimal;
import java.util.List;

/** A bundle sold at a percentage discount off the sum of its parts. Price follows the parts live. */
public final class ComboItem extends MenuItem {
    private final List<MenuItem> parts;
    private final BigDecimal discountPercent;

    public ComboItem(String restaurantId, String id, String name, List<MenuItem> parts, BigDecimal discountPercent,
                     String category, int prepMinutes, boolean available, BigDecimal stock) {
        super(restaurantId, id, name, bundle(parts, discountPercent), category, prepMinutes, available, stock);
        this.parts = List.copyOf(parts);
        this.discountPercent = discountPercent;
    }

    private static Money bundle(List<MenuItem> parts, BigDecimal pct) {
        if (parts == null || parts.isEmpty()) throw new InvalidInputException("A combo needs at least one item");
        if (pct == null || pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) >= 0)
            throw new InvalidInputException("Combo discount must be at least 0% and below 100%");
        Money sum = Money.ZERO;
        for (MenuItem p : parts) {
            if (p.allowsFractionalQuantity() || p instanceof ComboItem)
                throw new InvalidInputException("Combos can only bundle standard items (not '" + p.name() + "')");
            sum = sum.plus(p.price());
        }
        return sum.minus(sum.percent(pct));
    }

    public List<MenuItem> parts() { return parts; }
    public BigDecimal discountPercent() { return discountPercent; }
    @Override public Money price() { return bundle(parts, discountPercent); }
    @Override public boolean isAvailable() { return super.isAvailable() && parts.stream().allMatch(MenuItem::isAvailable); }
    @Override public String kind() { return "COMBO"; }
    @Override protected Money lineTotal(BigDecimal q) { return price().times(q.longValueExact()); }
}
