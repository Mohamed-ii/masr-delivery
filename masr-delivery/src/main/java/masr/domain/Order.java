package masr.domain;

import masr.exception.IllegalTransitionException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Identity = id. A pure state machine: it validates transitions and records when each state was
 * entered, but knows nothing about notifications (see OrderLifecycle). Built only by OrderBuilder.
 */
public final class Order {
    private final String id;
    private final Customer customer;
    private final Restaurant restaurant;
    private final Address address;
    private final List<OrderLine> lines;
    private final Promotion promotion;
    private final String notes;
    private final LocalDateTime placedAt;
    private final PriceBreakdown price;
    private final boolean priority;
    private final EnumMap<OrderStatus, LocalDateTime> history = new EnumMap<>(OrderStatus.class);
    private OrderStatus status = OrderStatus.PLACED;
    private String riderId;
    private boolean paid;

    Order(String id, Customer customer, Restaurant restaurant, Address address, List<OrderLine> lines,
          Promotion promotion, String notes, LocalDateTime placedAt, PriceBreakdown price) {
        this.id = id;
        this.customer = customer;
        this.restaurant = restaurant;
        this.address = address;
        this.lines = List.copyOf(lines);
        this.promotion = promotion;
        this.notes = notes;
        this.placedAt = placedAt;
        this.price = price;
        this.priority = customer.tier() == LoyaltyTier.GOLD;   // snapshot: Gold at the time of ordering
        history.put(OrderStatus.PLACED, placedAt);
    }

    public synchronized void advance(OrderStatus next, LocalDateTime at) throws IllegalTransitionException {
        if (!status.canTransitionTo(next))
            throw new IllegalTransitionException("Order " + id + " cannot move from " + status + " to " + next
                    + (status.isTerminal() ? " (" + status + " is final)" : ""));
        status = next;
        history.put(next, at);
    }

    public String id() { return id; }
    public Customer customer() { return customer; }
    public Restaurant restaurant() { return restaurant; }
    public Address address() { return address; }
    public District deliveryDistrict() { return address.district(); }
    public List<OrderLine> lines() { return lines; }
    public int itemCount() { return lines.size(); }
    public Optional<Promotion> promotion() { return Optional.ofNullable(promotion); }
    public String notes() { return notes; }
    public LocalDateTime placedAt() { return placedAt; }
    public PriceBreakdown price() { return price; }
    public boolean isPriority() { return priority; }
    public synchronized OrderStatus status() { return status; }
    public synchronized Map<OrderStatus, LocalDateTime> history() {
        return Collections.unmodifiableMap(new EnumMap<>(history));
    }
    public synchronized Optional<LocalDateTime> timeOf(OrderStatus s) { return Optional.ofNullable(history.get(s)); }
    public Optional<Duration> deliveryDuration() {
        return timeOf(OrderStatus.OUT_FOR_DELIVERY).flatMap(a -> timeOf(OrderStatus.DELIVERED)
                .map(b -> Duration.between(a, b)));
    }
    public synchronized String riderId() { return riderId; }
    public synchronized void assignRider(String id) { riderId = id; }
    public synchronized boolean isPaid() { return paid; }
    public synchronized void setPaid(boolean paid) { this.paid = paid; }

    @Override public boolean equals(Object o) { return o instanceof Order x && x.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return id + " [" + status() + "]"; }
}
