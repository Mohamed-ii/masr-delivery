package masr.service;

import masr.domain.Order;
import masr.domain.OrderStatus;

import java.time.LocalDateTime;

/** from is null for the initial PLACED event. */
public record OrderEvent(Order order, OrderStatus from, OrderStatus to, LocalDateTime at) { }
