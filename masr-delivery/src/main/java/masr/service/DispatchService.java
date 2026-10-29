package masr.service;

import masr.domain.Order;
import masr.domain.OrderStatus;
import masr.domain.Rider;
import masr.exception.MasrException;
import masr.exception.RiderUnavailableException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * READY orders wait in a PriorityQueue: Gold-at-order-time first, then longest-waiting (earliest READY
 * time), then id for a deterministic tie-break. Dispatch never lets a blocked head order starve the rest.
 */
public final class DispatchService {
    static final Comparator<Order> DISPATCH_ORDER =
            Comparator.comparing(Order::isPriority, Comparator.reverseOrder())
                    .thenComparing((Order o) -> o.timeOf(OrderStatus.READY).orElse(o.placedAt()))
                    .thenComparing(Order::id);

    private final PriorityQueue<Order> queue = new PriorityQueue<>(DISPATCH_ORDER);
    private final Repository<Rider> riders;
    private final OrderLifecycle lifecycle;

    public DispatchService(Repository<Rider> riders, OrderLifecycle lifecycle) {
        this.riders = riders;
        this.lifecycle = lifecycle;
    }

    public synchronized void enqueue(Order o) { if (!queue.contains(o)) queue.add(o); }
    public synchronized boolean remove(Order o) { return queue.remove(o); }
    /** Orders in the exact order they would be dispatched. */
    public synchronized List<Order> waiting() {
        List<Order> l = new ArrayList<>(queue);
        l.sort(DISPATCH_ORDER);
        return List.copyOf(l);
    }

    /** Assign as many waiting orders as there are eligible riders. Returns the orders assigned. */
    public synchronized List<Order> dispatch() {
        List<Order> assigned = new ArrayList<>();
        for (Order o : waiting()) {
            for (Rider r : candidates(o)) {
                try {
                    r.assign(o);                         // the one-active-order check happens here, atomically
                } catch (RiderUnavailableException busy) {
                    continue;                            // taken in the meantime: try the next rider
                }
                try {
                    o.assignRider(r.id());
                    lifecycle.transition(o, OrderStatus.ASSIGNED);
                    queue.remove(o);
                    assigned.add(o);
                } catch (MasrException e) {              // cannot happen for a READY order; stay safe
                    r.release(o);
                    o.assignRider(null);
                }
                break;
            }
        }
        return assigned;
    }

    private List<Rider> candidates(Order o) {
        return riders.stream()
                .filter(Rider::isAvailable)
                .filter(r -> r.canServe(o))
                .sorted(Comparator.comparingInt((Rider r) -> r.district().distanceKmTo(o.restaurant().district()))
                        .thenComparingInt(Rider::completedDeliveries)
                        .thenComparing(Rider::id))
                .toList();
    }
}
