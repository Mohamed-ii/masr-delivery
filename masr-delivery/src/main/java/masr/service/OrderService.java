package masr.service;

import masr.common.Money;
import masr.domain.Customer;
import masr.domain.Order;
import masr.domain.OrderBuilder;
import masr.domain.OrderLine;
import masr.domain.OrderStatus;
import masr.domain.PricingService;
import masr.domain.Promotion;
import masr.domain.Restaurant;
import masr.domain.Address;
import masr.exception.EntityNotFoundException;
import masr.exception.InsufficientStockException;
import masr.exception.MasrException;
import masr.exception.OrderStateException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class OrderService {
    private final Repository<Order> orders;
    private final Repository<masr.domain.Rider> riders;
    private final PricingService pricing;
    private final OrderLifecycle lifecycle;
    private final DispatchService dispatch;
    private final Clock clock;
    private final AtomicInteger seq = new AtomicInteger(1000);

    public OrderService(Repository<Order> orders, Repository<masr.domain.Rider> riders, PricingService pricing,
                        OrderLifecycle lifecycle, DispatchService dispatch, Clock clock) {
        this.orders = orders;
        this.riders = riders;
        this.pricing = pricing;
        this.lifecycle = lifecycle;
        this.dispatch = dispatch;
        this.clock = clock;
    }

    public OrderBuilder newOrder(Customer c, Restaurant r, Address a) { return newOrder(c, r, a, LocalDateTime.now(clock)); }
    public OrderBuilder newOrder(Customer c, Restaurant r, Address a, LocalDateTime at) {
        return new OrderBuilder("ORD-" + seq.incrementAndGet(), c, r, a, at);
    }

    /** Validate everything first (OrderBuilder.build), then reserve stock with rollback, then register. */
    public Order place(OrderBuilder builder) throws MasrException {
        Customer customer = builder.customer();
        boolean firstTime = customer.orderIds().stream().map(orders::find)
                .flatMap(java.util.Optional::stream).allMatch(o -> o.status() == OrderStatus.CANCELLED);
        Order order = builder.build(pricing, firstTime);

        List<OrderLine> reserved = new ArrayList<>();
        try {
            for (OrderLine l : order.lines()) {
                l.item().reserve(l.quantity());
                reserved.add(l);
            }
        } catch (InsufficientStockException e) {
            reserved.forEach(l -> l.item().release(l.quantity()));
            throw e;
        }
        orders.add(order);
        customer.addOrderId(order.id());
        lifecycle.announcePlaced(order);
        return order;
    }

    public Order findOwned(Customer customer, String ref) throws EntityNotFoundException {
        Order o = orders.get(ref);
        if (!o.customer().equals(customer))
            throw new EntityNotFoundException("No order " + ref.trim().toUpperCase() + " on your account");
        return o;
    }

    public void payFromWallet(Order order) throws MasrException {
        if (order.status() == OrderStatus.CANCELLED) throw new OrderStateException("Order " + order.id() + " is cancelled");
        if (order.isPaid()) throw new OrderStateException("Order " + order.id() + " is already paid");
        order.customer().debit(order.price().total());
        order.setPaid(true);
    }

    /** Returns the amount refunded to the wallet (zero if the order was never paid). */
    public Money cancel(Order order) throws MasrException { return cancel(order, lifecycle.now()); }
    public Money cancel(Order order, LocalDateTime at) throws MasrException {
        lifecycle.transition(order, OrderStatus.CANCELLED, at);   // throws IllegalTransitionException if not allowed
        order.lines().forEach(l -> l.item().release(l.quantity()));
        dispatch.remove(order);
        String riderId = order.riderId();
        if (riderId != null) riders.find(riderId).ifPresent(r -> r.release(order));
        Money refund = Money.ZERO;
        if (order.isPaid()) {
            refund = order.price().total();
            order.customer().credit(refund);
            order.setPaid(false);
        }
        if (riderId != null) dispatch.dispatch();                  // the freed rider can take the next order
        return refund;
    }

    public void accept(Order o) throws MasrException { lifecycle.transition(o, OrderStatus.ACCEPTED); }
    public Money reject(Order o) throws MasrException {
        if (o.status() != OrderStatus.PLACED)
            throw new OrderStateException("Only a pending (PLACED) order can be rejected; order " + o.id() + " is " + o.status());
        return cancel(o);
    }
    public void startPreparing(Order o) throws MasrException { lifecycle.transition(o, OrderStatus.PREPARING); }
    public void markReady(Order o) throws MasrException {
        lifecycle.transition(o, OrderStatus.READY);
        dispatch.enqueue(o);
        dispatch.dispatch();
    }
}
