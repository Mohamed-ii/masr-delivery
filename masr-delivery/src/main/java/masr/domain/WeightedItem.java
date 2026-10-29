package masr.domain;

import masr.common.Money;

import java.math.BigDecimal;

/** Priced per kilogram; the quantity ordered is a weight in kg. */
public final class WeightedItem extends MenuItem {
    public WeightedItem(String restaurantId, String id, String name, Money pricePerKg, String category,
                        int prepMinutes, boolean available, BigDecimal stockKg) {
        super(restaurantId, id, name, pricePerKg, category, prepMinutes, available, stockKg);
    }
    @Override public String kind() { return "WEIGHTED"; }
    @Override public String unit() { return "kg"; }
    @Override public boolean allowsFractionalQuantity() { return true; }
    @Override protected Money lineTotal(BigDecimal kg) { return price().times(kg); }
}
