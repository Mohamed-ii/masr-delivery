package masr.service;

import masr.common.Money;
import masr.domain.OrderStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Live platform counters, recalculated incrementally on every status change. */
public final class StatisticsListener implements OrderStatusListener {
    private final EnumMap<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
    private Money deliveredRevenue = Money.ZERO;

    @Override public synchronized void onStatusChanged(OrderEvent e) {
        if (e.from() != null) counts.merge(e.from(), -1L, Long::sum);
        counts.merge(e.to(), 1L, Long::sum);
        if (e.to() == OrderStatus.DELIVERED) deliveredRevenue = deliveredRevenue.plus(e.order().price().total());
    }
    public synchronized Map<OrderStatus, Long> countsByStatus() {
        EnumMap<OrderStatus, Long> copy = new EnumMap<>(OrderStatus.class);
        counts.forEach((k, v) -> { if (v > 0) copy.put(k, v); });
        return Collections.unmodifiableMap(copy);
    }
    public synchronized Money deliveredRevenue() { return deliveredRevenue; }
}
