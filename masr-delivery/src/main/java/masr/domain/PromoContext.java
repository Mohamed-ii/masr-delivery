package masr.domain;

import masr.common.Money;

import java.time.LocalDateTime;

public record PromoContext(Money subtotal, Money deliveryFee, District deliveryDistrict,
                           boolean firstTimeCustomer, LocalDateTime now) { }
