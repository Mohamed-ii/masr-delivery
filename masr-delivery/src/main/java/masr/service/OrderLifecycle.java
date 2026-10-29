package masr.service;

import masr.domain.Order;
import masr.domain.OrderStatus;
import masr.exception.IllegalTransitionException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The subject of the Observer pattern: the single route for status changes. */
public final class OrderLifecycle {
    private final List<OrderStatusListener> listeners = new CopyOnWriteArrayList<>();
    private final Clock clock;

    public OrderLifecycle(Clock clock) { this.clock = clock; }

    public void subscribe(OrderStatusListener l) { listeners.add(l); }
    public LocalDateTime now() { return LocalDateTime.now(clock); }

    public void transition(Order order, OrderStatus next) throws IllegalTransitionException {
        transition(order, next, now());
    }
    public void transition(Order order, OrderStatus next, LocalDateTime at) throws IllegalTransitionException {
        OrderStatus prev = order.status();
        order.advance(next, at);                       // throws if illegal: nothing is published
        publish(new OrderEvent(order, prev, next, at));
    }
    public void announcePlaced(Order order) {
        publish(new OrderEvent(order, null, OrderStatus.PLACED, order.placedAt()));
    }
    private void publish(OrderEvent e) {
        for (OrderStatusListener l : listeners) {
            try { l.onStatusChanged(e); }
            catch (RuntimeException ex) { System.err.println("Listener " + l.getClass().getSimpleName() + " failed: " + ex); }
        }
    }
}
