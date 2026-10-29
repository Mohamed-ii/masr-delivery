package masr.cli;

import masr.common.Money;
import masr.domain.Address;
import masr.domain.Customer;
import masr.domain.District;
import masr.domain.MenuItem;
import masr.domain.Order;
import masr.domain.OrderBuilder;
import masr.domain.Promotion;
import masr.domain.Restaurant;
import masr.exception.InvalidInputException;
import masr.exception.MasrException;
import masr.report.ReportService;
import masr.service.Platform;
import masr.service.RestaurantFilters;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

final class CustomerArea {
    private final Platform p;
    private final Console io;

    CustomerArea(Platform p, Console io) { this.p = p; this.io = io; }

    void run() {
        Customer c = login();
        if (c == null) return;
        while (true) {
            io.println("\n--- CUSTOMER: " + c.name() + " | tier " + c.tier() + " (" + c.tier().benefit()
                    + ") | wallet " + Console.egp(c.wallet()) + " ---");
            io.println("1. Browse restaurants   2. Search   3. View menu   4. Place order   5. Pay from wallet");
            io.println("6. Track order   7. Cancel order   8. Order history   9. Recent searches   10. Notifications");
            io.println("0. Back");
            int choice = io.askInt("Choose an option:", 0, 10);
            if (choice == 0) return;
            try {
                switch (choice) {
                    case 1 -> browse();
                    case 2 -> search(c);
                    case 3 -> pickRestaurant().ifPresent(r -> Views.printMenu(io, r));
                    case 4 -> placeOrder(c);
                    case 5 -> pay(c);
                    case 6 -> track(c);
                    case 7 -> cancel(c);
                    case 8 -> history(c);
                    case 9 -> recentSearches(c);
                    default -> notifications(c);
                }
            } catch (MasrException | InvalidInputException | DateTimeException e) {
                io.println("  !! " + e.getMessage());
            }
        }
    }

    private Customer login() {
        while (true) {
            Optional<String> id = io.askOptional("Customer ID (blank to go back):");
            if (id.isEmpty()) return null;
            Optional<Customer> c = p.customers.find(id.get());
            if (c.isPresent()) return c.get();
            io.println("  !! No customer with id " + id.get().toUpperCase());
        }
    }

    private void printRestaurants(List<Restaurant> list) {
        if (list.isEmpty()) { io.println("  Nothing found."); return; }
        for (int i = 0; i < list.size(); i++) io.println(String.format("  %2d. %s", i + 1, Views.restaurantLine(list.get(i))));
    }

    private Optional<Restaurant> pickRestaurant() {
        List<Restaurant> open = RestaurantFilters.search(p.restaurants.values(), RestaurantFilters.open());
        return io.choose("Open restaurants (best rated first):", open, Views::restaurantLine);
    }

    private void browse() {
        Predicate<Restaurant> filter = RestaurantFilters.open();
        Optional<String> district = io.askOptional("District (blank = any):");
        if (district.isPresent()) filter = filter.and(RestaurantFilters.inDistrict(District.parse(district.get())));
        Optional<String> cuisine = io.askOptional("Cuisine (blank = any):");
        if (cuisine.isPresent()) filter = filter.and(RestaurantFilters.cuisine(cuisine.get()));
        Optional<String> rating = io.askOptional("Minimum rating (blank = any):");
        if (rating.isPresent()) {
            try { filter = filter.and(RestaurantFilters.minRating(Double.parseDouble(rating.get()))); }
            catch (NumberFormatException e) { throw new InvalidInputException("Minimum rating must be a number"); }
        }
        Optional<String> ceiling = io.askOptional("Price ceiling in EGP (blank = any):");
        if (ceiling.isPresent()) filter = filter.and(RestaurantFilters.priceCeiling(Money.of(ceiling.get())));
        printRestaurants(RestaurantFilters.search(p.restaurants.values(), filter));
    }

    private void search(Customer c) {
        String text = io.askNonBlank("Search (name or cuisine):");
        c.recordSearch(text);
        printRestaurants(RestaurantFilters.search(p.restaurants.values(),
                RestaurantFilters.open().and(RestaurantFilters.textMatch(text))));
    }

    private void placeOrder(Customer c) throws MasrException {
        Optional<Restaurant> pick = pickRestaurant();
        if (pick.isEmpty()) return;
        Restaurant r = pick.get();
        Views.printMenu(io, r);
        List<MenuItem> menu = r.menu();
        if (menu.isEmpty()) throw new InvalidInputException("This restaurant has no menu items");

        Address address = chooseAddress(c);
        OrderBuilder builder = p.orderService.newOrder(c, r, address);
        while (true) {
            int n = io.askInt("Item number (0 = finished adding):", 0, menu.size());
            if (n == 0) break;
            MenuItem item = menu.get(n - 1);
            try {
                BigDecimal q = io.askPositiveDecimal(item.allowsFractionalQuantity()
                        ? "Kilograms of " + item.name() + " (e.g. 1.5):" : "Quantity of " + item.name() + ":");
                builder.addLine(item, q);
                io.println("  added.");
            } catch (InvalidInputException e) {
                io.println("  !! " + e.getMessage());
            }
        }
        while (true) {
            Optional<String> code = io.askOptional("Promo code (blank = none):");
            if (code.isEmpty()) break;
            Optional<Promotion> promo = p.findPromotion(code.get());
            if (promo.isPresent()) { builder.promotion(promo.get()); break; }
            io.println("  !! Unknown promotion code " + code.get().toUpperCase());
        }
        io.askOptional("Delivery notes (blank = none):").ifPresent(builder::notes);

        Order order = p.orderService.place(builder);               // all-or-nothing
        io.println("\nOrder placed.");
        Views.printSummary(io, order);
        io.println("Use 'Pay from wallet' with reference " + order.id() + " to pay.");
    }

    private Address chooseAddress(Customer c) {
        List<Address> addresses = c.addresses();
        io.println("Delivery address:");
        for (int i = 0; i < addresses.size(); i++) io.println("  " + (i + 1) + ". " + addresses.get(i));
        io.println("  " + (addresses.size() + 1) + ". Add a new address");
        int n = io.askInt("Choose:", 1, addresses.size() + 1);
        if (n <= addresses.size()) return addresses.get(n - 1);
        District d = District.parse(io.askNonBlank("District:"));
        return c.addAddress(d, io.askNonBlank("Address details:"));
    }

    private void pay(Customer c) throws MasrException {
        Order o = p.orderService.findOwned(c, io.askNonBlank("Order reference:"));
        p.orderService.payFromWallet(o);
        io.println("  Paid " + Console.egp(o.price().total()) + ". New wallet balance: " + Console.egp(c.wallet()));
    }

    private void track(Customer c) throws MasrException {
        Order o = p.orderService.findOwned(c, io.askNonBlank("Order reference:"));
        Duration since = Duration.between(o.placedAt(), LocalDateTime.now(p.clock));
        io.println("  Order " + o.id() + ": " + o.status() + " (placed " + Views.elapsed(since) + " ago, "
                + (o.isPaid() ? "paid" : "unpaid") + ")");
        o.history().forEach((s, t) -> io.println("     " + t.toLocalDate() + " " + t.toLocalTime().withNano(0) + "  " + s));
    }

    private void cancel(Customer c) throws MasrException {
        Order o = p.orderService.findOwned(c, io.askNonBlank("Order reference:"));
        Money refund = p.orderService.cancel(o);
        io.println("  Order " + o.id() + " cancelled."
                + (refund.isZero() ? " Nothing had been paid, so no refund is due."
                : " " + Console.egp(refund) + " refunded; wallet is now " + Console.egp(c.wallet())));
    }

    private void history(Customer c) {
        ReportService.History h = p.reports.customerHistory(c);
        if (h.orders().isEmpty()) { io.println("  No orders yet."); return; }
        for (Order o : h.orders())
            io.println(String.format("  %-9s %s %-22s %-17s %10s EGP", o.id(), o.placedAt().toLocalDate(),
                    o.restaurant().name(), o.status(), o.price().total()));
        io.println("  Lifetime spend (excluding cancelled): " + Console.egp(h.totalSpent()));
    }

    private void recentSearches(Customer c) {
        List<String> s = c.recentSearches();
        if (s.isEmpty()) io.println("  No searches yet.");
        for (int i = 0; i < s.size(); i++) io.println("  " + (i + 1) + ". " + s.get(i));
    }

    private void notifications(Customer c) {
        List<String> n = p.notifier.notificationsFor(c.id());
        if (n.isEmpty()) io.println("  No notifications.");
        n.forEach(x -> io.println("  " + x));
    }
}
