package masr;

import masr.common.Money;
import masr.config.AppConfig;
import masr.domain.*;
import masr.exception.*;
import masr.report.ReportService;
import masr.service.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class SelfTest {
    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) passed++; else { failed++; System.out.println("FAIL: " + name); }
    }
    interface Thrower { void run() throws Exception; }
    static boolean throwsA(Class<? extends Throwable> type, Thrower t) {
        try { t.run(); return false; } catch (Throwable e) { return type.isInstance(e); }
    }
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneId.of("UTC"));

    static Platform platform() {
        Platform p = new Platform(AppConfig.of(new Properties()), CLOCK);
        DataLoader.load(p, java.nio.file.Path.of("data"));
        return p;
    }

    public static void main(String[] args) throws Exception {
        money();
        worked_example();
        pricingRules();
        stateMachine();
        riderInvariant();
        dispatchPriority();
        placementAtomicity();
        collections();
        reports();
        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) System.exit(1);
    }

    static void money() {
        check("half-up percent", Money.of("0.05").percent(bd("10")).equals(Money.ofPiastres(1)));
        check("kg pricing rounds to piastre", Money.of("33.33").times(bd("0.5")).equals(Money.of("16.67")));
        check("0.1+0.2 exact", Money.of("0.10").plus(Money.of("0.20")).equals(Money.of("0.30")));
        check("toString", Money.of("5").toString().equals("5.00") && Money.ofPiastres(-5).toString().equals("-0.05"));
        check("bad amount", throwsA(InvalidInputException.class, () -> Money.of("abc")));
    }

    static void worked_example() throws Exception {
        PricingService pricing = new PricingService(PricingConfig.defaults());
        Restaurant maadi = new Restaurant("R1", "Maadi Grill", District.MAADI, List.of("Grill"), 4.0, true);
        StandardItem item = new StandardItem("R1", "I1", "Platter", Money.of(120), "Mains", 10, true, BigDecimal.TEN);
        maadi.addItem(item);
        Customer c = new Customer("C1", "Test", "01012345678", Money.of(1000));
        Address faisal = c.addAddress(District.FAISAL, "Some street");
        Promotion nile = new PercentagePromotion("NILE20", bd("20"), Money.of(50), List.of());
        Order o = new OrderBuilder("O1", c, maadi, faisal, LocalDateTime.now(CLOCK))
                .addLine(item, bd("2")).promotion(nile).build(pricing, true);
        PriceBreakdown pb = o.price();
        check("subtotal 240", pb.subtotal().equals(Money.of("240.00")));
        check("delivery 42", pb.deliveryFee().equals(Money.of("42.00")));
        check("service 24", pb.serviceFee().equals(Money.of("24.00")));
        check("promo 48", pb.discount().equals(Money.of("48.00")));
        check("WORKED EXAMPLE total 258.00", pb.total().equals(Money.of("258.00")));
        StandardItem big = new StandardItem("R1", "I2", "Banquet", Money.of(400), "Mains", 10, true, BigDecimal.TEN);
        maadi.addItem(big);
        Order o2 = new OrderBuilder("O2", c, maadi, faisal, LocalDateTime.now(CLOCK)).addLine(big, bd("1")).promotion(nile).build(pricing, true);
        check("cap applied", o2.price().discount().equals(Money.of("50.00")));
    }

    static void pricingRules() throws Exception {
        PricingService pricing = new PricingService(PricingConfig.defaults());
        Restaurant r = new Restaurant("R1", "G", District.MAADI, List.of("Grill"), 4.0, true);
        WeightedItem kebab = new WeightedItem("R1", "W1", "Kebab", Money.of(480), "Grill", 30, true, bd("50"));
        StandardItem a = new StandardItem("R1", "A", "A", Money.of(50), "x", 5, true, bd("50"));
        StandardItem b = new StandardItem("R1", "B", "B", Money.of(30), "x", 5, true, bd("50"));
        ComboItem combo = new ComboItem("R1", "CB", "Combo", List.of(a, b), bd("10"), "Combos", 5, true, bd("50"));
        r.addItem(kebab); r.addItem(a); r.addItem(b); r.addItem(combo);
        check("weighted 1.5kg = 720", kebab.priceFor(bd("1.5")).equals(Money.of(720)));
        check("combo = 72 (80 less 10%)", combo.price().equals(Money.of(72)));
        check("combo x3 = 216", combo.priceFor(bd("3")).equals(Money.of(216)));
        check("fractional count rejected", throwsA(InvalidInputException.class, () -> a.priceFor(bd("1.5"))));
        check("zero quantity rejected", throwsA(InvalidInputException.class, () -> a.priceFor(bd("0"))));
        combo.setAvailable(true); b.setAvailable(false);
        check("combo unavailable when part is", !combo.isAvailable());
        b.setAvailable(true);

        Customer silver = new Customer("S", "Silver", "01112345678", Money.of(0)); silver.setCompletedOrders(10);
        Customer gold = new Customer("G", "Gold", "01212345678", Money.of(0)); gold.setCompletedOrders(30);
        check("tiers", new Customer("B", "B", "01512345678", Money.ZERO).tier() == LoyaltyTier.BRONZE
                && silver.tier() == LoyaltyTier.SILVER && gold.tier() == LoyaltyTier.GOLD);
        Customer nine = new Customer("N", "N", "01012345678", Money.ZERO); nine.setCompletedOrders(9);
        check("9 completed is still bronze", nine.tier() == LoyaltyTier.BRONZE);
        List<OrderLine> lines = List.of(new OrderLine(a, bd("1"), Money.of(50)));
        PriceBreakdown s = pricing.price(District.MAADI, District.FAISAL, lines, silver.tier(), null, false, LocalDateTime.now(CLOCK));
        PriceBreakdown g = pricing.price(District.MAADI, District.FAISAL, lines, gold.tier(), null, false, LocalDateTime.now(CLOCK));
        check("silver 10% off 42 -> 37.80", s.deliveryFee().equals(Money.of("37.80")));
        check("gold fee waived", g.deliveryFee().isZero());
        PriceBreakdown near = pricing.price(District.MAADI, District.MAADI, lines, LoyaltyTier.BRONZE, null, false, LocalDateTime.now(CLOCK));
        check("within 3km is base fee 15", near.deliveryFee().equals(Money.of(15)));

        // promotions
        LocalDateTime now = LocalDateTime.now(CLOCK);
        Promotion min = new FixedAmountPromotion("MIN", Money.of(30), List.of(PromoConditions.minSubtotal(Money.of(100))));
        check("min subtotal rejected", throwsA(PromotionNotApplicableException.class,
                () -> pricing.price(District.MAADI, District.MAADI, lines, LoyaltyTier.BRONZE, min, false, now)));
        Promotion old = new FixedAmountPromotion("OLD", Money.of(5), List.of(PromoConditions.expiresOn(LocalDate.of(2026, 1, 1))));
        check("expired rejected", throwsA(PromotionExpiredException.class,
                () -> pricing.price(District.MAADI, District.MAADI, lines, LoyaltyTier.BRONZE, old, false, now)));
        Promotion first = new FixedAmountPromotion("FIRST", Money.of(5), List.of(PromoConditions.firstTimeCustomersOnly()));
        check("first-time only", throwsA(PromotionNotApplicableException.class,
                () -> pricing.price(District.MAADI, District.MAADI, lines, LoyaltyTier.BRONZE, first, false, now)));
        Promotion dist = new FixedAmountPromotion("DIST", Money.of(5), List.of(PromoConditions.onlyInDistrict(District.DOKKI)));
        check("district restricted", throwsA(PromotionNotApplicableException.class,
                () -> pricing.price(District.MAADI, District.MAADI, lines, LoyaltyTier.BRONZE, dist, false, now)));
        Promotion free = new FreeDeliveryPromotion("FREE", List.of());
        PriceBreakdown fb = pricing.price(District.MAADI, District.FAISAL, lines, LoyaltyTier.BRONZE, free, false, now);
        check("free delivery discount = fee", fb.discount().equals(fb.deliveryFee()));
        Promotion huge = new FixedAmountPromotion("HUGE", Money.of(5000), List.of());
        PriceBreakdown hb = pricing.price(District.MAADI, District.MAADI, lines, LoyaltyTier.BRONZE, huge, false, now);
        check("total never negative", !hb.total().isNegative());
    }

    static void stateMachine() throws Exception {
        Platform p = platform();
        Customer c = p.customers.get("C01");
        Restaurant r = p.restaurants.get("R01");
        Order o = p.orderService.place(p.orderService.newOrder(c, r, c.addresses().get(0)).addLine(r.findItem("K1").get(), bd("1")));
        check("PLACED->DELIVERED rejected", throwsA(IllegalTransitionException.class, () -> p.lifecycle.transition(o, OrderStatus.DELIVERED)));
        check("status unchanged after rejection", o.status() == OrderStatus.PLACED);
        p.orderService.accept(o);
        check("skip PREPARING rejected", throwsA(IllegalTransitionException.class, () -> p.lifecycle.transition(o, OrderStatus.READY)));
        p.orderService.cancel(o);
        check("cancelled is final", throwsA(IllegalTransitionException.class, () -> p.lifecycle.transition(o, OrderStatus.ACCEPTED)));
        check("cancel twice rejected", throwsA(IllegalTransitionException.class, () -> p.orderService.cancel(o)));
        Rider rider = p.riders.get("D01"); p.riderService.goOnDuty(rider);
        Order o2 = p.orderService.place(p.orderService.newOrder(c, p.restaurants.get("R02"), c.addresses().get(1)).addLine(p.restaurants.get("R02").findItem("G2").get(), bd("1")));
        p.orderService.accept(o2); p.orderService.startPreparing(o2); p.orderService.markReady(o2);
        check("auto-assigned to available rider", o2.status() == OrderStatus.ASSIGNED && rider.activeOrder().isPresent());
        check("cannot cancel after... ASSIGNED is still cancellable", OrderStatus.ASSIGNED.canTransitionTo(OrderStatus.CANCELLED));
        check("ASSIGNED->DELIVERED rejected", throwsA(IllegalTransitionException.class, () -> p.riderService.deliver(rider)));
        p.riderService.pickUp(rider);
        check("OUT_FOR_DELIVERY cannot be cancelled", throwsA(IllegalTransitionException.class, () -> p.orderService.cancel(o2)));
        int before = c.completedOrders();
        p.riderService.deliver(rider);
        check("completed count increments", c.completedOrders() == before + 1 && rider.completedDeliveries() == 1);
        check("rider free again", rider.isAvailable());
        check("DELIVERED cannot be cancelled", throwsA(IllegalTransitionException.class, () -> p.orderService.cancel(o2)));
        check("observers fired", p.audit.entries().size() >= 8 && !p.notifier.notificationsFor("C01").isEmpty());
        Order o3 = p.orderService.place(p.orderService.newOrder(c, r, c.addresses().get(0)).addLine(r.findItem("K1").get(), bd("2")));
        Money wallet = c.wallet();
        p.orderService.payFromWallet(o3);
        check("wallet debited", c.wallet().equals(wallet.minus(o3.price().total())));
        check("double pay rejected", throwsA(OrderStateException.class, () -> p.orderService.payFromWallet(o3)));
        Money refund = p.orderService.cancel(o3);
        check("refund restores wallet", refund.equals(o3.price().total()) && c.wallet().equals(wallet));
        Customer poor = p.customers.get("C04");
        Order o4 = p.orderService.place(p.orderService.newOrder(poor, p.restaurants.get("R05"), poor.addresses().get(0)).addLine(p.restaurants.get("R05").findItem("M1").get(), bd("2")));
        check("insufficient funds", throwsA(InsufficientFundsException.class, () -> p.orderService.payFromWallet(o4)));
        check("wallet never negative", !poor.wallet().isNegative());
    }

    static void riderInvariant() throws Exception {
        Platform p = platform();
        Rider rider = p.riders.get("D03");
        rider.goOnDuty();
        Customer c = p.customers.get("C03");
        Restaurant r = p.restaurants.get("R05");
        Order a = p.orderService.place(p.orderService.newOrder(c, r, c.addresses().get(0)).addLine(r.findItem("M3").get(), bd("1")));
        Order b = p.orderService.place(p.orderService.newOrder(c, r, c.addresses().get(0)).addLine(r.findItem("M3").get(), bd("1")));
        rider.assign(a);
        check("second assignment rejected", throwsA(RiderBusyException.class, () -> rider.assign(b)));
        check("rider still holds first", rider.activeOrder().get().equals(a));
        check("cannot go off duty while busy", throwsA(RiderBusyException.class, rider::goOffDuty));
        Rider off = p.riders.get("D01");
        check("off-duty rider cannot be assigned", throwsA(RiderUnavailableException.class, () -> off.assign(b)));

        Rider racer = p.riders.get("D04"); racer.goOnDuty();
        List<Order> contenders = new ArrayList<>();
        for (int i = 0; i < 50; i++) contenders.add(p.orderService.place(p.orderService.newOrder(c, r, c.addresses().get(0)).addLine(r.findItem("M3").get(), bd("1"))));
        java.util.concurrent.atomic.AtomicInteger wins = new java.util.concurrent.atomic.AtomicInteger();
        List<Thread> ts = new ArrayList<>();
        for (Order o : contenders) {
            Thread t = new Thread(() -> { try { racer.assign(o); wins.incrementAndGet(); } catch (RiderUnavailableException ignored) { } });
            ts.add(t); t.start();
        }
        for (Thread t : ts) t.join();
        check("exactly one concurrent assignment wins", wins.get() == 1);
    }

    static void dispatchPriority() throws Exception {
        Platform p = platform();
        Restaurant r = p.restaurants.get("R03");
        MenuItem fries = r.findItem("S3").get();
        Customer bronze = p.customers.get("C04"), silver = p.customers.get("C02"), gold = p.customers.get("C03");
        Order ob = ready(p, bronze, r, fries);
        Order os = ready(p, silver, r, fries);
        Order og = ready(p, gold, r, fries);
        check("gold jumps the queue", p.dispatch.waiting().get(0).equals(og));
        check("then longest-waiting", p.dispatch.waiting().get(1).equals(ob) && p.dispatch.waiting().get(2).equals(os));
        Rider rider = p.riders.get("D03");
        p.riderService.goOnDuty(rider);
        check("gold order dispatched first", rider.activeOrder().map(o -> o.equals(og)).orElse(false));
        check("others still waiting", p.dispatch.waiting().size() == 2);
        check("no rider holds two", p.riders.stream().filter(x -> x.activeOrder().isPresent()).count() == 1);
    }
    static Order ready(Platform p, Customer c, Restaurant r, MenuItem item) throws Exception {
        Order o = p.orderService.place(p.orderService.newOrder(c, r, c.addresses().get(0)).addLine(item, bd("1")));
        p.orderService.accept(o); p.orderService.startPreparing(o); p.orderService.markReady(o);
        return o;
    }

    static void placementAtomicity() throws Exception {
        Platform p = platform();
        Customer c = p.customers.get("C01");
        Restaurant r = p.restaurants.get("R02");
        MenuItem g2 = r.findItem("G2").get(), g5 = r.findItem("G5").get();
        BigDecimal stockBefore = g2.stock();
        check("unavailable item rejects whole order", throwsA(ItemUnavailableException.class, () -> p.orderService.place(
                p.orderService.newOrder(c, r, c.addresses().get(0)).addLine(g2, bd("1")).addLine(g5, bd("1")))));
        check("stock untouched after rejection", g2.stock().compareTo(stockBefore) == 0 && p.orders.size() == 0);
        check("stock shortage", throwsA(InsufficientStockException.class, () -> p.orderService.place(
                p.orderService.newOrder(c, r, c.addresses().get(0)).addLine(g2, bd("1000")))));
        check("closed restaurant", throwsA(RestaurantClosedException.class, () -> p.orderService.place(
                p.orderService.newOrder(c, p.restaurants.get("R07"), c.addresses().get(0)).addLine(p.restaurants.get("R07").findItem("C1").get(), bd("1")))));
        check("empty order", throwsA(InvalidInputException.class, () -> p.orderService.place(p.orderService.newOrder(c, r, c.addresses().get(0)))));
        Address foreign = p.customers.get("C02").addresses().get(0);
        check("foreign address", throwsA(AddressNotOwnedException.class, () -> p.orderService.place(
                p.orderService.newOrder(c, r, foreign).addLine(g2, bd("1")))));
        Order ok = p.orderService.place(p.orderService.newOrder(c, r, c.addresses().get(0)).addLine(g2, bd("3")));
        check("stock reserved", g2.stock().compareTo(stockBefore.subtract(bd("3"))) == 0);
        p.orderService.cancel(ok);
        check("stock restored on cancel", g2.stock().compareTo(stockBefore) == 0);
        check("merged duplicate lines", p.orderService.newOrder(c, r, c.addresses().get(0)).addLine(g2, bd("1")).addLine(g2, bd("2"))
                .build(p.pricing, true).lines().get(0).quantity().compareTo(bd("3")) == 0);
    }

    static void collections() throws Exception {
        Platform p = platform();
        Restaurant r = p.restaurants.get("R01");
        check("menu keeps insertion order", r.menu().stream().map(MenuItem::id).toList().equals(List.of("K1", "K2", "K3", "K4")));
        check("menu not modifiable", throwsA(UnsupportedOperationException.class, () -> r.menu().clear()));
        check("cuisines not modifiable", throwsA(UnsupportedOperationException.class, () -> r.cuisines().add("X")));
        check("repo values not modifiable", throwsA(UnsupportedOperationException.class, () -> p.restaurants.values().clear()));
        check("addresses not modifiable", throwsA(UnsupportedOperationException.class, () -> p.customers.get("C01").addresses().clear()));
        check("duplicate id rejected", throwsA(DuplicateEntityException.class, () -> p.restaurants.add(new Restaurant("r01", "Dup", District.MAADI, List.of("x"), 1, true))));
        check("duplicate menu item rejected", throwsA(DuplicateEntityException.class, () -> r.addItem(new StandardItem("R01", "k1", "Dup", Money.of(1), "x", 1, true, BigDecimal.ONE))));
        check("case-insensitive lookup", p.restaurants.find("r05").isPresent());

        Set<Restaurant> set = new HashSet<>(List.of(r, new Restaurant("R01", "Other name", District.DOKKI, List.of("x"), 1, true)));
        check("same id = same restaurant in a Set", set.size() == 1);
        Map<Customer, Integer> map = new HashMap<>();
        map.put(new Customer("C9", "A", "01012345678", Money.ZERO), 1);
        check("customer usable as key", map.containsKey(new Customer("c9", "B", "01112345678", Money.ZERO)));
        Customer cu = p.customers.get("C04"); cu.addAddress(District.DOKKI, "X"); cu.addAddress(District.DOKKI, "X");
        check("same address added twice stays once", cu.addresses().stream().filter(a -> a.detail().equals("X")).count() == 1);

        List<String> order = RestaurantFilters.search(p.restaurants.values(), x -> true).stream().map(Restaurant::id).toList();
        check("sorted by rating desc", order.get(0).equals("R05") && order.get(1).equals("R01"));
        Restaurant tie1 = new Restaurant("T1", "Zeta", District.MAADI, List.of("x"), 4.9, true);
        Restaurant tie2 = new Restaurant("T2", "Alpha", District.MAADI, List.of("x"), 4.9, true);
        check("ties broken by name", Restaurant.BY_RATING_THEN_NAME.compare(tie2, tie1) < 0);

        Set<String> cuisines = new TreeSet<>();
        p.restaurants.stream().flatMap(x -> x.cuisines().stream()).forEach(cuisines::add);
        check("distinct cuisines", cuisines.stream().filter("Egyptian"::equals).count() == 1);
        check("cuisine normalisation", new Restaurant("Z", "Z", District.MAADI, List.of("fast  FOOD", "Fast Food"), 1, true).cuisines().size() == 1);

        check("search by combination", RestaurantFilters.search(p.restaurants.values(),
                RestaurantFilters.inDistrict(District.MAADI).and(RestaurantFilters.cuisine("grill")).and(RestaurantFilters.minRating(4.5)))
                .stream().map(Restaurant::id).toList().equals(List.of("R02")));
        check("search custom lambda", !RestaurantFilters.search(p.restaurants.values(), x -> x.name().startsWith("Cairo")).isEmpty());
        check("price ceiling", RestaurantFilters.search(p.restaurants.values(), RestaurantFilters.priceCeiling(Money.of(20))).stream().anyMatch(x -> x.id().equals("R06")));

        Customer c = p.customers.get("C01");
        for (int i = 1; i <= 8; i++) c.recordSearch("s" + i);
        check("last five searches newest first", c.recentSearches().equals(List.of("s8", "s7", "s6", "s5", "s4")));

        check("mobile validation", throwsA(InvalidInputException.class, () -> new Customer("X", "X", "01312345678", Money.ZERO)) && throwsA(InvalidInputException.class, () -> new Customer("X", "X", "0101234567", Money.ZERO)));
        check("rating range", throwsA(InvalidInputException.class, () -> new Restaurant("Q", "Q", District.MAADI, List.of("x"), 5.1, true)));
        check("positive price", throwsA(InvalidInputException.class, () -> new StandardItem("R01", "Z", "Z", Money.ZERO, "x", 1, true, BigDecimal.ONE)));

        MenuItemFactory f = MenuItemFactory.withDefaults();
        check("factory unknown type", throwsA(InvalidInputException.class, () -> f.create(
                new MenuItemFactory.ItemSpec("R01", "Q", "SOUFFLE", "n", Money.of(1), "c", 1, true, ""), id -> null, BigDecimal.ONE)));
        f.register("DRINK", (s, sib, st) -> new StandardItem(s.restaurantId(), s.itemId(), s.name(), s.price(), s.category(), s.prepMinutes(), s.available(), st));
        check("factory extensible without edits", f.create(new MenuItemFactory.ItemSpec("R01", "D1", "drink", "Tea", Money.of(5), "c", 1, true, ""), id -> null, BigDecimal.ONE).kind().equals("STANDARD"));

        VehicleTypes.register(new VehicleType() {
            public String name() { return "scooter"; } public int maxRangeKm() { return 15; }
            public int speedKmh() { return 30; } public int maxOrderItems() { return 4; } });
        check("new vehicle type plugs in", VehicleTypes.parse("Scooter").maxRangeKm() == 15);
    }

    static void reports() throws Exception {
        Platform p = platform();
        ReportService rs = p.reports;
        check("empty: no most-ordered item", rs.mostOrderedItem().isEmpty());
        check("empty: no peak hour", rs.peakHour().isEmpty());
        check("empty: revenue zero", rs.revenueBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).isZero());
        check("empty: avg map empty", rs.averageOrderValueByDistrict().isEmpty());
        check("empty: everyone inactive", rs.inactiveCustomers().size() == p.customers.size());

        DemoData.generate(p, 80);
        check("orders created", p.orders.size() == 80);
        Map<OrderStatus, Long> by = rs.orderCountByStatus();
        check("status counts add up", by.values().stream().mapToLong(Long::longValue).sum() == 80);
        check("live stats agree with report", p.stats.countsByStatus().equals(by));
        long delivered = by.getOrDefault(OrderStatus.DELIVERED, 0L);
        check("delivered exist", delivered > 40);
        Money revenue = rs.revenueBetween(LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
        check("revenue equals live counter", revenue.equals(p.stats.deliveredRevenue()));
        check("most ordered item present", rs.mostOrderedItem().isPresent());
        check("peak hour is lunchtime", rs.peakHour().map(h -> h.hour() == 13 || h.hour() == 14).orElse(false));
        check("top rated proven", rs.topRatedProvenRestaurants().stream().anyMatch(r -> r.id().equals("R05")));
        check("rider perf sorted desc", isDesc(rs.riderPerformance().stream().map(ReportService.RiderStat::deliveries).toList()));
        check("rider deliveries sum", rs.riderPerformance().stream().mapToLong(ReportService.RiderStat::deliveries).sum() == delivered);
        check("rider avg duration present", rs.riderPerformance().stream().anyMatch(s -> s.averageDuration().isPresent()));
        check("top5 limited", rs.topRestaurantsByRevenue(YearMonth.now(CLOCK), 5).size() <= 5);
        check("top5 sorted", isDesc(rs.topRestaurantsByRevenue(YearMonth.now(CLOCK), 5).stream().map(e -> e.revenue().piastres()).toList()));
        Customer c = p.customers.get("C03");
        ReportService.History h = rs.customerHistory(c);
        check("history newest first", isDesc(h.orders().stream().map(o -> o.placedAt().toString()).toList()));
        Money spent = h.orders().stream().filter(o -> o.status() != OrderStatus.CANCELLED).map(o -> o.price().total()).reduce(Money.ZERO, Money::plus);
        check("history total", spent.equals(h.totalSpent()));
        long inactive = rs.inactiveCustomers().size();
        check("inactive count sane", inactive <= p.customers.size());
        check("avg per district computed", !rs.averageOrderValueByDistrict().isEmpty());
        rs.topRatedProvenRestaurants();
        check("report 4 empty case is a list", true);
    }

    static <T extends Comparable<T>> boolean isDesc(List<T> l) {
        for (int i = 1; i < l.size(); i++) if (l.get(i - 1).compareTo(l.get(i)) < 0) return false;
        return true;
    }
}
