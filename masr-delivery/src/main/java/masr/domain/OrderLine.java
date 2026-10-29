package masr.domain;

import masr.common.Money;

import java.math.BigDecimal;

/** A menu item, the quantity ordered (count or kilograms) and the line total priced at order time. */
public record OrderLine(MenuItem item, BigDecimal quantity, Money total) { }
