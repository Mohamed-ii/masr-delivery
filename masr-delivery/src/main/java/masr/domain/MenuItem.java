package masr.domain;

import masr.common.Money;
import masr.common.Validation;
import masr.exception.InsufficientStockException;
import masr.exception.InvalidInputException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Template for every kind of menu item. Pricing is polymorphic: the order code only ever calls
 * priceFor(quantity), so a new kind of item is a new subclass and nothing else changes.
 * Identity (equals/hashCode) = (restaurantId, id): the id is unique within its restaurant.
 */
public abstract class MenuItem {
    private final String restaurantId;
    private final String id;
    private final String name;
    private final String category;
    private final int prepMinutes;
    private final Money price;
    private boolean available;
    private BigDecimal stock;

    protected MenuItem(String restaurantId, String id, String name, Money price, String category,
                       int prepMinutes, boolean available, BigDecimal stock) {
        this.restaurantId = Validation.id(restaurantId, "Restaurant id");
        this.id = Validation.id(id, "Item id");
        this.name = Validation.nonBlank(name, "Item name");
        this.price = Validation.positive(price, "Price");
        this.category = Validation.nonBlank(category, "Category");
        this.prepMinutes = Validation.positive(prepMinutes, "Preparation time");
        this.available = available;
        if (stock == null || stock.signum() < 0) throw new InvalidInputException("Stock must not be negative");
        this.stock = stock;
    }

    public abstract String kind();
    /** Unit label shown next to quantities. */
    public String unit() { return "x"; }
    public boolean allowsFractionalQuantity() { return false; }
    protected abstract Money lineTotal(BigDecimal quantity);

    public final Money priceFor(BigDecimal quantity) {
        Validation.positive(quantity, "Quantity");
        if (!allowsFractionalQuantity() && quantity.stripTrailingZeros().scale() > 0)
            throw new InvalidInputException("'" + name + "' must be ordered in whole units");
        return lineTotal(quantity);
    }

    public String restaurantId() { return restaurantId; }
    public String id() { return id; }
    public String name() { return name; }
    public String category() { return category; }
    public int prepMinutes() { return prepMinutes; }
    public Money price() { return price; }
    public synchronized boolean isAvailable() { return available; }
    public synchronized void setAvailable(boolean a) { available = a; }
    public synchronized BigDecimal stock() { return stock; }
    public synchronized void setStock(BigDecimal s) {
        if (s == null || s.signum() < 0) throw new InvalidInputException("Stock must not be negative");
        stock = s;
    }
    public synchronized boolean hasStock(BigDecimal q) { return stock.compareTo(q) >= 0; }
    public synchronized void reserve(BigDecimal q) throws InsufficientStockException {
        if (!hasStock(q))
            throw new InsufficientStockException("Only " + stock.stripTrailingZeros().toPlainString() + " "
                    + unit() + " of '" + name + "' left in stock");
        stock = stock.subtract(q);
    }
    public synchronized void release(BigDecimal q) { stock = stock.add(q); }

    @Override public boolean equals(Object o) {
        return o instanceof MenuItem m && m.restaurantId.equals(restaurantId) && m.id.equals(id);
    }
    @Override public int hashCode() { return Objects.hash(restaurantId, id); }
    @Override public String toString() { return id + " " + name; }
}
