package masr.domain;

import masr.common.Money;

import java.math.BigDecimal;

/** Sold at its listed price: price x count. */
public final class StandardItem extends MenuItem {
    public StandardItem(String restaurantId, String id, String name, Money price, String category,
                        int prepMinutes, boolean available, BigDecimal stock) {
        super(restaurantId, id, name, price, category, prepMinutes, available, stock);
    }
    @Override public String kind() { return "STANDARD"; }
    @Override protected Money lineTotal(BigDecimal q) { return price().times(q.longValueExact()); }
}
