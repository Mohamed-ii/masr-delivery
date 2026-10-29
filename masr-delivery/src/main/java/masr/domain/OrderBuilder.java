package masr.domain;

import masr.exception.AddressNotOwnedException;
import masr.exception.InsufficientStockException;
import masr.exception.InvalidInputException;
import masr.exception.ItemUnavailableException;
import masr.exception.MasrException;
import masr.exception.RestaurantClosedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builder: customer, restaurant and address are mandatory (constructor); lines, promotion and notes
 * are fluent and optional. build() validates EVERYTHING before returning and mutates NOTHING,
 * which is what makes order placement atomic.
 */
public final class OrderBuilder {
    private final String id;
    private final Customer customer;
    private final Restaurant restaurant;
    private final Address address;
    private final LocalDateTime placedAt;
    private final Map<MenuItem, BigDecimal> quantities = new LinkedHashMap<>();
    private Promotion promotion;
    private String notes = "";

    public OrderBuilder(String id, Customer customer, Restaurant restaurant, Address address, LocalDateTime placedAt) {
        this.id = Objects.requireNonNull(id);
        this.customer = Objects.requireNonNull(customer);
        this.restaurant = Objects.requireNonNull(restaurant);
        this.address = Objects.requireNonNull(address);
        this.placedAt = Objects.requireNonNull(placedAt);
    }

    /** Adding the same item twice merges the quantities. */
    public OrderBuilder addLine(MenuItem item, BigDecimal quantity) {
        BigDecimal merged = quantities.getOrDefault(item, BigDecimal.ZERO).add(quantity);
        item.priceFor(merged);                       // fails fast on bad quantity (<=0, fractional count)
        quantities.put(item, merged);
        return this;
    }
    public OrderBuilder promotion(Promotion p) { this.promotion = p; return this; }
    public OrderBuilder notes(String n) { this.notes = n == null ? "" : n.trim(); return this; }

    public Customer customer() { return customer; }
    public String id() { return id; }

    public Order build(PricingService pricing, boolean firstTimeCustomer) throws MasrException {
        if (quantities.isEmpty()) throw new InvalidInputException("An order must contain at least one line item");
        if (!restaurant.isOpen())
            throw new RestaurantClosedException(restaurant.name() + " is closed and cannot take orders");
        if (!customer.addresses().contains(address))
            throw new AddressNotOwnedException("That delivery address does not belong to " + customer.name());

        List<OrderLine> lines = new ArrayList<>();
        for (Map.Entry<MenuItem, BigDecimal> e : quantities.entrySet()) {
            MenuItem item = e.getKey();
            BigDecimal q = e.getValue();
            if (restaurant.findItem(item.id()).filter(m -> m == item).isEmpty())
                throw new ItemUnavailableException("'" + item.name() + "' is not on " + restaurant.name() + "'s menu");
            if (!item.isAvailable())
                throw new ItemUnavailableException("'" + item.name() + "' is currently unavailable");
            if (!item.hasStock(q))
                throw new InsufficientStockException("Only " + item.stock().stripTrailingZeros().toPlainString()
                        + " " + item.unit() + " of '" + item.name() + "' left in stock (you asked for "
                        + q.stripTrailingZeros().toPlainString() + ")");
            lines.add(new OrderLine(item, q, item.priceFor(q)));
        }
        PriceBreakdown price = pricing.price(restaurant.district(), address.district(), lines,
                customer.tier(), promotion, firstTimeCustomer, placedAt);
        return new Order(id, customer, restaurant, address, lines, promotion, notes, placedAt, price);
    }
}
