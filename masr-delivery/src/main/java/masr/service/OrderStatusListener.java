package masr.service;

/** Observer: implement and subscribe; Order and OrderService never change. */
public interface OrderStatusListener {
    void onStatusChanged(OrderEvent event);
}
