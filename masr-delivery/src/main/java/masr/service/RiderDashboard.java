package masr.service;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Latest dispatch-related status line per rider. */
public final class RiderDashboard implements OrderStatusListener {
    private final Map<String, String> board = new TreeMap<>();

    @Override public synchronized void onStatusChanged(OrderEvent e) {
        String rider = e.order().riderId();
        if (rider == null) return;
        board.put(rider, "order " + e.order().id() + " -> " + e.to() + " at " + e.at().toLocalTime().withNano(0));
    }
    public synchronized Map<String, String> view() { return Collections.unmodifiableMap(new TreeMap<>(board)); }
}
