package masr.service;

import masr.domain.Order;
import masr.domain.OrderStatus;
import masr.domain.Rider;
import masr.exception.MasrException;
import masr.exception.OrderStateException;
import masr.exception.RiderBusyException;

public final class RiderService {
    private final OrderLifecycle lifecycle;
    private final DispatchService dispatch;

    public RiderService(OrderLifecycle lifecycle, DispatchService dispatch) {
        this.lifecycle = lifecycle;
        this.dispatch = dispatch;
    }

    public void goOnDuty(Rider r) {
        r.goOnDuty();
        dispatch.dispatch();
    }
    public void goOffDuty(Rider r) throws RiderBusyException { r.goOffDuty(); }

    public Order pickUp(Rider r) throws MasrException {
        Order o = r.activeOrder().orElseThrow(() -> new OrderStateException("You have no active order"));
        lifecycle.transition(o, OrderStatus.OUT_FOR_DELIVERY);
        return o;
    }
    public Order deliver(Rider r) throws MasrException {
        Order o = r.activeOrder().orElseThrow(() -> new OrderStateException("You have no active order"));
        lifecycle.transition(o, OrderStatus.DELIVERED);           // rejects ASSIGNED -> DELIVERED
        r.completeDelivery(o, o.deliveryDistrict());
        o.customer().incrementCompleted();
        dispatch.dispatch();
        return o;
    }
}
