package masr.service;

import java.util.ArrayList;
import java.util.List;

public final class AuditLog implements OrderStatusListener {
    private final List<String> entries = new ArrayList<>();

    @Override public synchronized void onStatusChanged(OrderEvent e) {
        entries.add(e.at() + " | " + e.order().id() + " | " + (e.from() == null ? "-" : e.from()) + " -> " + e.to());
    }
    public synchronized List<String> entries() { return List.copyOf(entries); }
}
