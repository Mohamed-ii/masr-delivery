package masr.cli;

import masr.domain.ComboItem;
import masr.domain.MenuItem;
import masr.domain.Order;
import masr.domain.OrderLine;
import masr.domain.PriceBreakdown;
import masr.domain.Restaurant;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Shared formatting for restaurants, menus and orders. */
final class Views {
    private Views() { }

    static String restaurantLine(Restaurant r) {
        return String.format("%-24s %-11s %-26s rating %.1f%s", r.name(), r.district(),
                String.join(", ", r.cuisines()), r.rating(), r.isOpen() ? "" : "  [CLOSED]");
    }

    static void printMenu(Console io, Restaurant r) {
        List<MenuItem> items = r.menu();
        io.println("--- Menu: " + r.name() + " (" + r.district() + ") ---");
        if (items.isEmpty()) { io.println("  (menu is empty)"); return; }
        Map<String, List<Integer>> byCategory = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++)
            byCategory.computeIfAbsent(items.get(i).category(), k -> new java.util.ArrayList<>()).add(i);
        byCategory.forEach((cat, idx) -> {
            io.println(" " + cat.toUpperCase());
            for (int i : idx) {
                MenuItem m = items.get(i);
                String price = m.price() + " EGP" + (m.allowsFractionalQuantity() ? " per " + m.unit() : "");
                String extra = m instanceof ComboItem c
                        ? "  (" + c.parts().stream().map(MenuItem::name).collect(Collectors.joining(" + "))
                          + ", " + c.discountPercent().stripTrailingZeros().toPlainString() + "% off)" : "";
                io.println(String.format("  %2d. %-24s %-16s ~%d min  stock %s%s%s", i + 1, m.name(), price,
                        m.prepMinutes(), m.stock().stripTrailingZeros().toPlainString(),
                        m.isAvailable() ? "" : "  [UNAVAILABLE]", extra));
            }
        });
    }

    static void printSummary(Console io, Order o) {
        PriceBreakdown p = o.price();
        io.println("Order " + o.id() + " - " + o.restaurant().name() + " -> " + o.address());
        for (OrderLine l : o.lines())
            io.println(String.format("   %-24s %6s %-3s %12s EGP", l.item().name(),
                    l.quantity().stripTrailingZeros().toPlainString(), l.item().unit(), l.total()));
        io.println(String.format("   %-34s %12s EGP", "Subtotal", p.subtotal()));
        io.println(String.format("   %-34s %12s EGP", "Delivery fee (" + o.customer().tier() + " tier)", p.deliveryFee()));
        io.println(String.format("   %-34s %12s EGP", "Service fee", p.serviceFee()));
        if (p.promoCode() != null)
            io.println(String.format("   %-34s %12s EGP", "Promotion " + p.promoCode(), "-" + p.discount()));
        io.println(String.format("   %-34s %12s EGP", "TOTAL", p.total()));
        if (!o.notes().isEmpty()) io.println("   Notes: " + o.notes());
    }

    static String elapsed(Duration d) {
        long s = Math.max(0, d.toSeconds());
        return String.format("%dh %02dm %02ds", s / 3600, (s % 3600) / 60, s % 60);
    }
}
