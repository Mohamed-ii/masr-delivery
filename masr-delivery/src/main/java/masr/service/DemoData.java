package masr.service;

import masr.domain.Address;
import masr.domain.Customer;
import masr.domain.MenuItem;
import masr.domain.Order;
import masr.domain.OrderBuilder;
import masr.domain.OrderStatus;
import masr.domain.Restaurant;
import masr.domain.Rider;
import masr.exception.MasrException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/** Generates a deterministic ~45-day order history so every report has something to show. */
public final class DemoData {
    private DemoData() { }

    public static int generate(Platform p, int count) throws MasrException {
        Random rnd = new Random(42);
        List<Restaurant> open = p.restaurants.stream().filter(Restaurant::isOpen)
                .filter(r -> r.menu().stream().anyMatch(MenuItem::isAvailable)).toList();
        List<Customer> customers = p.customers.values();
        List<Rider> riders = p.riders.values();
        Restaurant favourite = p.restaurants.find("R05").orElse(open.get(0));
        LocalDateTime now = p.lifecycle.now();
        int created = 0;

        for (int i = 0; i < count; i++) {
            Restaurant r = (i % 3 == 0) ? favourite : open.get(rnd.nextInt(open.size()));
            Customer c = customers.get(rnd.nextInt(customers.size()));
            Address a = c.addresses().get(rnd.nextInt(c.addresses().size()));
            int hour = (rnd.nextInt(3) == 0) ? 8 + rnd.nextInt(14) : 13 + rnd.nextInt(2);   // lunch peak
            LocalDateTime at = now.minusDays(rnd.nextInt(45)).withHour(hour).withMinute(rnd.nextInt(60)).withSecond(0).withNano(0);
            if (at.isAfter(now)) at = at.minusDays(1);

            OrderBuilder b = p.orderService.newOrder(c, r, a, at);
            List<MenuItem> items = r.menu().stream().filter(MenuItem::isAvailable).toList();
            int lines = 1 + rnd.nextInt(2);
            for (int k = 0; k < lines; k++) {
                MenuItem it = items.get(rnd.nextInt(items.size()));
                b.addLine(it, it.allowsFractionalQuantity() ? new BigDecimal("0.5").add(BigDecimal.valueOf(rnd.nextInt(3)).multiply(new BigDecimal("0.5")))
                        : BigDecimal.valueOf(1 + rnd.nextInt(2)));
            }
            Order o = p.orderService.place(b);
            created++;

            if (i % 10 == 0) { p.orderService.cancel(o, at.plusMinutes(3)); continue; }
            if (i % 15 == 1) { p.lifecycle.transition(o, OrderStatus.ACCEPTED, at.plusMinutes(2)); continue; }

            Rider rider = riders.get(rnd.nextInt(riders.size()));
            LocalDateTime t = at;
            p.lifecycle.transition(o, OrderStatus.ACCEPTED, t = t.plusMinutes(2));
            p.lifecycle.transition(o, OrderStatus.PREPARING, t = t.plusMinutes(3));
            p.lifecycle.transition(o, OrderStatus.READY, t = t.plusMinutes(15));
            rider.goOnDuty();
            rider.assign(o);
            o.assignRider(rider.id());
            p.lifecycle.transition(o, OrderStatus.ASSIGNED, t = t.plusMinutes(2));
            p.lifecycle.transition(o, OrderStatus.OUT_FOR_DELIVERY, t = t.plusMinutes(3));
            p.lifecycle.transition(o, OrderStatus.DELIVERED, t.plusMinutes(12 + rnd.nextInt(30)));
            rider.completeDelivery(o, o.deliveryDistrict());
            c.incrementCompleted();
            o.setPaid(true);
        }
        riders.forEach(r -> { try { r.goOffDuty(); } catch (MasrException ignored) { } });
        return created;
    }
}
