package masr.cli;

import masr.common.Money;
import masr.domain.MenuItem;
import masr.domain.MenuItemFactory.ItemSpec;
import masr.domain.Order;
import masr.domain.OrderStatus;
import masr.domain.Restaurant;
import masr.exception.InvalidInputException;
import masr.exception.MasrException;
import masr.service.Platform;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

final class RestaurantArea {
    private final Platform p;
    private final Console io;

    RestaurantArea(Platform p, Console io) { this.p = p; this.io = io; }

    void run() {
        Restaurant r = login();
        if (r == null) return;
        while (true) {
            io.println("\n--- RESTAURANT: " + r.name() + " (" + (r.isOpen() ? "OPEN" : "CLOSED") + ") ---");
            io.println("1. Pending orders (accept/reject)   2. Mark preparing   3. Mark ready");
            io.println("4. Toggle item availability   5. Add menu item   6. Remove menu item   7. Adjust stock");
            io.println("8. Today's orders & revenue   9. Open/close restaurant   0. Back");
            int choice = io.askInt("Choose an option:", 0, 9);
            if (choice == 0) return;
            try {
                switch (choice) {
                    case 1 -> pending(r);
                    case 2 -> advance(r, OrderStatus.ACCEPTED, "Accepted orders", o -> p.orderService.startPreparing(o), "is now PREPARING");
                    case 3 -> advance(r, OrderStatus.PREPARING, "Orders being prepared", o -> p.orderService.markReady(o), "is READY (dispatching)");
                    case 4 -> toggle(r);
                    case 5 -> addItem(r);
                    case 6 -> removeItem(r);
                    case 7 -> adjustStock(r);
                    case 8 -> today(r);
                    default -> { r.setOpen(!r.isOpen()); io.println("  Restaurant is now " + (r.isOpen() ? "OPEN" : "CLOSED")); }
                }
            } catch (MasrException | InvalidInputException | DateTimeException e) {
                io.println("  !! " + e.getMessage());
            }
        }
    }

    @FunctionalInterface private interface Action { void apply(Order o) throws MasrException; }

    private Restaurant login() {
        while (true) {
            Optional<String> id = io.askOptional("Restaurant ID (blank to go back):");
            if (id.isEmpty()) return null;
            Optional<Restaurant> r = p.restaurants.find(id.get());
            if (r.isPresent()) return r.get();
            io.println("  !! No restaurant with id " + id.get().toUpperCase());
        }
    }

    private List<Order> ordersOf(Restaurant r, OrderStatus status) {
        return p.orders.stream().filter(o -> o.restaurant().equals(r) && o.status() == status).toList();
    }

    private void pending(Restaurant r) throws MasrException {
        Optional<Order> pick = io.choose("Pending orders:", ordersOf(r, OrderStatus.PLACED),
                o -> o.id() + "  " + o.customer().name() + "  " + o.lines().size() + " line(s)  " + o.price().total() + " EGP");
        if (pick.isEmpty()) return;
        Order o = pick.get();
        Views.printSummary(io, o);
        if (io.askYesNo("Accept this order? (n = reject)")) {
            p.orderService.accept(o);
            io.println("  Order " + o.id() + " accepted.");
        } else {
            Money refund = p.orderService.reject(o);
            io.println("  Order " + o.id() + " rejected." + (refund.isZero() ? "" : " Customer refunded " + Console.egp(refund) + "."));
        }
    }

    private void advance(Restaurant r, OrderStatus from, String title, Action action, String outcome) throws MasrException {
        Optional<Order> pick = io.choose(title + ":", ordersOf(r, from), o -> o.id() + "  " + o.customer().name());
        if (pick.isEmpty()) return;
        action.apply(pick.get());
        io.println("  Order " + pick.get().id() + " " + outcome + ".");
    }

    private Optional<MenuItem> pickItem(Restaurant r) {
        return io.choose("Menu items:", r.menu(), m -> m.id() + "  " + m.name() + "  [" + (m.isAvailable() ? "available" : "UNAVAILABLE") + "]");
    }

    private void toggle(Restaurant r) {
        pickItem(r).ifPresent(m -> {
            m.setAvailable(!m.isAvailable());
            io.println("  " + m.name() + " is now " + (m.isAvailable() ? "available" : "unavailable"));
        });
    }

    private void addItem(Restaurant r) throws MasrException {
        io.println("Item types: " + p.itemFactory.types());
        String type = io.askNonBlank("Type:");
        String id = io.askNonBlank("Item id (unique in this restaurant):");
        String name = io.askNonBlank("Name:");
        BigDecimal price = io.askNonNegativeDecimal("Price in EGP (per kg for WEIGHTED; ignored for COMBO):");
        String category = io.askNonBlank("Category:");
        int prep = io.askInt("Preparation time (minutes):", 1, 600);
        String extra = io.ask("Extra data (COMBO only, e.g. K2+K3@10; blank otherwise):");
        ItemSpec spec = new ItemSpec(r.id(), id, type, name, Money.of(price), category, prep, true, extra);
        MenuItem item = p.itemFactory.create(spec, i -> r.findItem(i).orElse(null), p.config.defaultStock());
        r.addItem(item);
        io.println("  Added " + item.name() + " (" + item.kind() + ") at " + item.price() + " EGP.");
    }

    private void removeItem(Restaurant r) throws MasrException {
        Optional<MenuItem> m = pickItem(r);
        if (m.isPresent()) {
            r.removeItem(m.get().id());
            io.println("  Removed " + m.get().name());
        }
    }

    private void adjustStock(Restaurant r) {
        Optional<MenuItem> m = pickItem(r);
        if (m.isEmpty()) return;
        m.get().setStock(io.askNonNegativeDecimal("New stock (" + m.get().unit() + "):"));
        io.println("  Stock of " + m.get().name() + " is now " + m.get().stock().stripTrailingZeros().toPlainString());
    }

    private void today(Restaurant r) {
        LocalDate today = LocalDate.now(p.clock);
        List<Order> list = p.orders.stream()
                .filter(o -> o.restaurant().equals(r) && o.placedAt().toLocalDate().equals(today)).toList();
        if (list.isEmpty()) io.println("  No orders today.");
        list.forEach(o -> io.println(String.format("  %-9s %-17s %10s EGP", o.id(), o.status(), o.price().total())));
        Money revenue = list.stream().filter(o -> o.status() == OrderStatus.DELIVERED)
                .map(o -> o.price().total()).reduce(Money.ZERO, Money::plus);
        io.println("  Revenue today (delivered orders): " + Console.egp(revenue));
    }
}
