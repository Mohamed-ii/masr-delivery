package masr.cli;

import masr.common.Money;
import masr.domain.Customer;
import masr.domain.District;
import masr.domain.FixedAmountPromotion;
import masr.domain.FreeDeliveryPromotion;
import masr.domain.PercentagePromotion;
import masr.domain.PromoCondition;
import masr.domain.PromoConditions;
import masr.domain.Promotion;
import masr.domain.Restaurant;
import masr.domain.Rider;
import masr.domain.VehicleTypes;
import masr.exception.InvalidInputException;
import masr.exception.MasrException;
import masr.exception.OrderStateException;
import masr.report.ReportService;
import masr.service.DemoData;
import masr.service.Platform;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class AdminArea {
    private final Platform p;
    private final Console io;

    AdminArea(Platform p, Console io) { this.p = p; this.io = io; }

    /** Launch promotions so the app is usable out of the box. */
    static void seedPromotions(Platform p) {
        try {
            p.promotions.add(new PercentagePromotion("NILE20", BigDecimal.valueOf(20), Money.of(50), List.of()));
            p.promotions.add(new FixedAmountPromotion("WELCOME30", Money.of(30),
                    List.of(PromoConditions.firstTimeCustomersOnly(), PromoConditions.minSubtotal(Money.of(100)))));
            p.promotions.add(new FreeDeliveryPromotion("FREESHIP", List.of(PromoConditions.minSubtotal(Money.of(150)))));
        } catch (MasrException e) {
            throw new IllegalStateException(e);
        }
    }

    void run() {
        while (true) {
            io.println("\n--- ADMIN & REPORTS ---");
            io.println("1. Add restaurant   2. Remove restaurant   3. Create promotion   4. Reports");
            io.println("5. Platform statistics   6. Register customer   7. Register rider");
            io.println("8. Audit log (last 15)   9. Load demo order history   0. Back");
            int choice = io.askInt("Choose an option:", 0, 9);
            if (choice == 0) return;
            try {
                switch (choice) {
                    case 1 -> addRestaurant();
                    case 2 -> removeRestaurant();
                    case 3 -> createPromotion();
                    case 4 -> reports();
                    case 5 -> platformStats();
                    case 6 -> registerCustomer();
                    case 7 -> registerRider();
                    case 8 -> { List<String> e = p.audit.entries(); e.subList(Math.max(0, e.size() - 15), e.size()).forEach(x -> io.println("  " + x)); }
                    default -> io.println("  Generated " + DemoData.generate(p, 80) + " historical orders.");
                }
            } catch (MasrException | InvalidInputException | DateTimeException e) {
                io.println("  !! " + e.getMessage());
            }
        }
    }

    private void addRestaurant() throws MasrException {
        String id = io.askNonBlank("Restaurant id:");
        String name = io.askNonBlank("Name:");
        District d = District.parse(io.askNonBlank("District:"));
        List<String> cuisines = Arrays.asList(io.askNonBlank("Cuisines (comma separated):").split(","));
        double rating = io.askRating("Rating (0.0 - 5.0):");
        p.restaurants.add(new Restaurant(id, name, d, cuisines, rating, io.askYesNo("Open now?")));
        io.println("  Restaurant added.");
    }

    private void removeRestaurant() throws MasrException {
        Restaurant r = p.restaurants.get(io.askNonBlank("Restaurant id:"));
        if (p.orders.stream().anyMatch(o -> o.restaurant().equals(r) && !o.status().isTerminal()))
            throw new OrderStateException(r.name() + " still has active orders and cannot be removed");
        p.restaurants.remove(r.id());
        io.println("  Removed " + r.name());
    }

    private void createPromotion() throws MasrException {
        String code = io.askNonBlank("Code:");
        int type = io.askInt("Type: 1 = percentage with cap, 2 = fixed amount, 3 = free delivery:", 1, 3);
        List<PromoCondition> conditions = new ArrayList<>();
        Promotion promo;
        Object[] params = new Object[2];
        if (type == 1) { params[0] = io.askPositiveDecimal("Percent off subtotal:"); params[1] = io.askMoney("Maximum discount (EGP):"); }
        if (type == 2) params[0] = io.askMoney("Amount off (EGP):");
        io.askOptional("Minimum subtotal (blank = none):").ifPresent(s -> conditions.add(PromoConditions.minSubtotal(Money.of(s))));
        io.askOptional("Last valid day yyyy-MM-dd (blank = never expires):").ifPresent(s -> conditions.add(PromoConditions.expiresOn(LocalDate.parse(s))));
        io.askOptional("Restrict to district (blank = all):").ifPresent(s -> conditions.add(PromoConditions.onlyInDistrict(District.parse(s))));
        if (io.askYesNo("First-time customers only?")) conditions.add(PromoConditions.firstTimeCustomersOnly());
        promo = switch (type) {
            case 1 -> new PercentagePromotion(code, (BigDecimal) params[0], (Money) params[1], conditions);
            case 2 -> new FixedAmountPromotion(code, (Money) params[0], conditions);
            default -> new FreeDeliveryPromotion(code, conditions);
        };
        p.promotions.add(promo);
        io.println("  Created " + promo.describe());
    }

    private void registerCustomer() throws MasrException {
        Customer c = new Customer(io.askNonBlank("Customer id:"), io.askNonBlank("Name:"),
                io.askNonBlank("Mobile (11 digits, 010/011/012/015):"), Money.of(io.askNonNegativeDecimal("Opening wallet (EGP):")));
        c.addAddress(District.parse(io.askNonBlank("Address district:")), io.askNonBlank("Address details:"));
        p.customers.add(c);
        io.println("  Registered " + c.name() + " (tier " + c.tier() + ").");
    }

    private void registerRider() throws MasrException {
        String id = io.askNonBlank("Rider id:");
        String name = io.askNonBlank("Name:");
        io.println("Vehicles: " + VehicleTypes.all());
        Rider r = new Rider(id, name, VehicleTypes.parse(io.askNonBlank("Vehicle:")), District.parse(io.askNonBlank("Current district:")));
        p.riders.add(r);
        io.println("  Registered rider " + r.name() + ".");
    }

    private void platformStats() {
        io.println("  Restaurants: " + p.restaurants.size() + " | Customers: " + p.customers.size()
                + " | Riders: " + p.riders.size() + " | Orders: " + p.orders.size());
        io.println("  Cuisines on the platform: " + p.restaurants.stream().flatMap(r -> r.cuisines().stream())
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new)));
        io.println("  Live orders by status: " + p.stats.countsByStatus());
        io.println("  Delivered revenue (live counter): " + Console.egp(p.stats.deliveredRevenue()));
        io.println("  READY orders waiting for a rider: " + p.dispatch.waiting().stream().map(o -> o.id() + (o.isPriority() ? "*" : "")).toList()
                + "  (* = Gold priority)");
    }

    private void reports() {
        io.println("\nReports: 1 Revenue by date range  2 Top 5 restaurants (month)  3 Avg order value by district");
        io.println("         4 Top-rated proven restaurants  5 Orders by status  6 Rider performance");
        io.println("         7 Most ordered item  8 Customer history  9 Peak ordering hour  10 Inactive customers");
        int n = io.askInt("Report number (0 = back):", 0, 10);
        ReportService rs = p.reports;
        switch (n) {
            case 1 -> {
                LocalDate from = LocalDate.parse(io.askNonBlank("From (yyyy-MM-dd):"));
                LocalDate to = LocalDate.parse(io.askNonBlank("To (yyyy-MM-dd):"));
                io.println("  Revenue " + from + " .. " + to + ": " + Console.egp(rs.revenueBetween(from, to)));
            }
            case 2 -> {
                YearMonth m = YearMonth.parse(io.askNonBlank("Month (yyyy-MM):"));
                List<ReportService.RevenueEntry> top = rs.topRestaurantsByRevenue(m, 5);
                if (top.isEmpty()) io.println("  No delivered orders in " + m);
                for (int i = 0; i < top.size(); i++) io.println("  " + (i + 1) + ". " + top.get(i).restaurant().name() + "  " + Console.egp(top.get(i).revenue()));
            }
            case 3 -> {
                var avg = rs.averageOrderValueByDistrict();
                if (avg.isEmpty()) io.println("  No orders yet.");
                avg.forEach((d, v) -> io.println("  " + d + ": " + Console.egp(v)));
            }
            case 4 -> {
                List<Restaurant> list = rs.topRatedProvenRestaurants();
                if (list.isEmpty()) io.println("  No restaurant has rating > 4.5 with at least 20 completed orders.");
                list.forEach(r -> io.println("  " + Views.restaurantLine(r)));
            }
            case 5 -> {
                var counts = rs.orderCountByStatus();
                if (counts.isEmpty()) io.println("  No orders yet.");
                counts.forEach((s, c) -> io.println("  " + s + ": " + c));
            }
            case 6 -> rs.riderPerformance().forEach(s -> io.println("  " + s.rider().name() + ": " + s.deliveries() + " deliveries, avg "
                    + s.averageDuration().map(Duration::toMinutes).map(x -> x + " min").orElse("n/a")));
            case 7 -> io.println(rs.mostOrderedItem()
                    .map(c -> "  Most ordered: " + c.item().name() + " (" + c.item().restaurantId() + ") - " + c.timesOrdered() + " times")
                    .orElse("  No orders have been placed yet, so there is no most-ordered item."));
            case 8 -> {
                Customer c = p.customers.find(io.askNonBlank("Customer id:")).orElseThrow(() -> new InvalidInputException("Unknown customer"));
                ReportService.History h = rs.customerHistory(c);
                h.orders().forEach(o -> io.println(String.format("  %-9s %s %-17s %10s EGP", o.id(), o.placedAt(), o.status(), o.price().total())));
                io.println("  Total spent: " + Console.egp(h.totalSpent()));
            }
            case 9 -> io.println(rs.peakHour()
                    .map(h -> String.format("  Peak hour: %02d:00-%02d:59 with %d orders", h.hour(), h.hour(), h.orders()))
                    .orElse("  No orders yet, so there is no peak hour."));
            case 10 -> {
                List<Customer> list = rs.inactiveCustomers();
                if (list.isEmpty()) io.println("  Every customer ordered recently.");
                list.forEach(c -> io.println("  " + c.id() + " " + c.name()));
            }
            default -> { }
        }
    }
}
