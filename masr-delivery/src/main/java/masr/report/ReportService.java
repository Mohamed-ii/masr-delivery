package masr.report;

import masr.common.Money;
import masr.domain.Customer;
import masr.domain.District;
import masr.domain.MenuItem;
import masr.domain.Order;
import masr.domain.OrderLine;
import masr.domain.OrderStatus;
import masr.domain.Restaurant;
import masr.domain.Rider;
import masr.service.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Part D: every report is a declarative stream pipeline over the live repositories.
 * "Revenue" = total of DELIVERED orders. "Order value" and "spent" ignore CANCELLED orders.
 */
public final class ReportService {
    public record RevenueEntry(Restaurant restaurant, Money revenue) { }
    public record RiderStat(Rider rider, long deliveries, Optional<Duration> averageDuration) { }
    public record ItemCount(MenuItem item, long timesOrdered) { }
    public record HourCount(int hour, long orders) { }
    public record History(List<Order> orders, Money totalSpent) { }

    private static final Predicate<Order> NOT_CANCELLED = o -> o.status() != OrderStatus.CANCELLED;
    private static final Predicate<Order> DELIVERED = o -> o.status() == OrderStatus.DELIVERED;

    private final Repository<Order> orders;
    private final Repository<Restaurant> restaurants;
    private final Repository<Rider> riders;
    private final Repository<Customer> customers;
    private final Clock clock;
    private final int inactiveDays;

    public ReportService(Repository<Order> orders, Repository<Restaurant> restaurants, Repository<Rider> riders,
                         Repository<Customer> customers, Clock clock, int inactiveDays) {
        this.orders = orders;
        this.restaurants = restaurants;
        this.riders = riders;
        this.customers = customers;
        this.clock = clock;
        this.inactiveDays = inactiveDays;
    }

    private static Money sum(java.util.stream.Stream<Order> s) {
        return s.map(o -> o.price().total()).reduce(Money.ZERO, Money::plus);
    }

    /** 1. Total revenue for a date range (inclusive), by the day the order was placed. */
    public Money revenueBetween(LocalDate from, LocalDate to) {
        return sum(orders.stream().filter(DELIVERED)
                .filter(o -> !o.placedAt().toLocalDate().isBefore(from) && !o.placedAt().toLocalDate().isAfter(to)));
    }

    /** 2. Top restaurants by revenue in a month. */
    public List<RevenueEntry> topRestaurantsByRevenue(YearMonth month, int limit) {
        return orders.stream().filter(DELIVERED)
                .filter(o -> YearMonth.from(o.placedAt()).equals(month))
                .collect(Collectors.groupingBy(Order::restaurant,
                        Collectors.reducing(Money.ZERO, o -> o.price().total(), Money::plus)))
                .entrySet().stream()
                .map(e -> new RevenueEntry(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(RevenueEntry::revenue).reversed()
                        .thenComparing(e -> e.restaurant().name()))
                .limit(limit)
                .toList();
    }

    /** 3. Average order value per delivery district (exact: sum / count, rounded HALF_UP to a piastre). */
    public Map<District, Money> averageOrderValueByDistrict() {
        return orders.stream().filter(NOT_CANCELLED)
                .collect(Collectors.groupingBy(Order::deliveryDistrict, () -> new EnumMap<>(District.class),
                        Collectors.collectingAndThen(
                                Collectors.summarizingLong(o -> o.price().total().piastres()),
                                st -> Money.of(java.math.BigDecimal.valueOf(st.getSum(), 2)
                                        .divide(java.math.BigDecimal.valueOf(st.getCount()), 2,
                                                java.math.RoundingMode.HALF_UP)))));
    }

    /** 4. Rating above 4.5 and at least 20 completed orders. */
    public List<Restaurant> topRatedProvenRestaurants() {
        Map<Restaurant, Long> completed = orders.stream().filter(DELIVERED)
                .collect(Collectors.groupingBy(Order::restaurant, Collectors.counting()));
        return restaurants.stream()
                .filter(r -> r.rating() > 4.5)
                .filter(r -> completed.getOrDefault(r, 0L) >= 20)
                .sorted(Restaurant.BY_RATING_THEN_NAME)
                .toList();
    }

    /** 5. Orders grouped by current status. */
    public Map<OrderStatus, Long> orderCountByStatus() {
        return orders.stream().collect(Collectors.groupingBy(Order::status,
                () -> new EnumMap<>(OrderStatus.class), Collectors.counting()));
    }

    /** 6. Per rider: deliveries and average delivery time (pickup to drop-off), most deliveries first. */
    public List<RiderStat> riderPerformance() {
        Map<String, List<Order>> byRider = orders.stream().filter(DELIVERED).filter(o -> o.riderId() != null)
                .collect(Collectors.groupingBy(Order::riderId));
        return riders.stream()
                .map(r -> {
                    List<Order> mine = byRider.getOrDefault(r.id(), List.of());
                    OptionalDouble avg = mine.stream().map(Order::deliveryDuration).flatMap(Optional::stream)
                            .mapToLong(Duration::toSeconds).average();
                    return new RiderStat(r, mine.size(), avg.isPresent()
                            ? Optional.of(Duration.ofSeconds(Math.round(avg.getAsDouble()))) : Optional.empty());
                })
                .sorted(Comparator.comparingLong(RiderStat::deliveries).reversed()
                        .thenComparing((RiderStat s) -> s.rider().name()))
                .toList();
    }

    /** 7. Most frequently ordered item, or Optional.empty() when nothing has been ordered. */
    public Optional<ItemCount> mostOrderedItem() {
        return orders.stream().filter(NOT_CANCELLED)
                .flatMap(o -> o.lines().stream())
                .map(OrderLine::item)
                .collect(Collectors.groupingBy(i -> i, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new ItemCount(e.getKey(), e.getValue()))
                .max(Comparator.comparingLong(ItemCount::timesOrdered)
                        .thenComparing((ItemCount c) -> c.item().name(), Comparator.reverseOrder()));
    }

    /** 8. A customer's history, newest first, with lifetime spend. */
    public History customerHistory(Customer c) {
        List<Order> list = c.orderIds().stream().map(orders::find).flatMap(Optional::stream)
                .sorted(Comparator.comparing(Order::placedAt).reversed().thenComparing(Order::id, Comparator.reverseOrder()))
                .toList();
        return new History(list, sum(list.stream().filter(NOT_CANCELLED)));
    }

    /** 9. Busiest hour of the day (ties: earliest hour); empty when there are no orders. */
    public Optional<HourCount> peakHour() {
        return orders.stream()
                .collect(Collectors.groupingBy(o -> o.placedAt().getHour(), Collectors.counting()))
                .entrySet().stream()
                .map(e -> new HourCount(e.getKey(), e.getValue()))
                .max(Comparator.comparingLong(HourCount::orders).thenComparing(HourCount::hour, Comparator.reverseOrder()));
    }

    /** 10. Customers with no order in the last N days (including those who never ordered). */
    public List<Customer> inactiveCustomers() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(inactiveDays);
        Set<Customer> recent = orders.stream().filter(o -> !o.placedAt().isBefore(cutoff))
                .map(Order::customer).collect(Collectors.toSet());
        return customers.stream().filter(c -> !recent.contains(c)).sorted(Comparator.comparing(Customer::name)).toList();
    }
}
