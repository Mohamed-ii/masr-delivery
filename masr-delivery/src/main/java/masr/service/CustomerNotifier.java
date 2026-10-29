package masr.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CustomerNotifier implements OrderStatusListener {
    private final Map<String, List<String>> inbox = new HashMap<>();

    @Override public synchronized void onStatusChanged(OrderEvent e) {
        String text = switch (e.to()) {
            case PLACED -> "Order " + e.order().id() + " was placed.";
            case ACCEPTED -> "Order " + e.order().id() + " was accepted by " + e.order().restaurant().name() + ".";
            case PREPARING -> "Order " + e.order().id() + " is being prepared.";
            case READY -> "Order " + e.order().id() + " is ready and waiting for a rider.";
            case ASSIGNED -> "A rider was assigned to order " + e.order().id() + ".";
            case OUT_FOR_DELIVERY -> "Order " + e.order().id() + " is on its way!";
            case DELIVERED -> "Order " + e.order().id() + " was delivered. Enjoy!";
            case CANCELLED -> "Order " + e.order().id() + " was cancelled.";
        };
        inbox.computeIfAbsent(e.order().customer().id(), k -> new ArrayList<>()).add(e.at().toLocalTime().withNano(0) + "  " + text);
    }
    public synchronized List<String> notificationsFor(String customerId) {
        return List.copyOf(inbox.getOrDefault(customerId, List.of()));
    }
}
