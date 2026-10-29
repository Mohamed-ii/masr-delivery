package masr.domain;

import masr.common.Money;
import masr.common.Validation;
import masr.exception.PromotionException;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Strategy + template method: discountFor() checks every condition, then asks the concrete type
 * how much to take off. The pricing engine only ever sees this class, so a new promotion type is a new
 * subclass and PricingService is untouched. Identity = code (codes are case-insensitive).
 */
public abstract class Promotion {
    private final String code;
    private final List<PromoCondition> conditions;

    protected Promotion(String code, List<PromoCondition> conditions) {
        this.code = Validation.nonBlank(code, "Promotion code").toUpperCase(Locale.ROOT);
        this.conditions = List.copyOf(conditions);
    }

    public final Money discountFor(PromoContext ctx) throws PromotionException {
        for (PromoCondition c : conditions) c.check(ctx);
        Money d = compute(ctx);
        return d.isNegative() ? Money.ZERO : d;
    }

    protected abstract Money compute(PromoContext ctx);
    protected abstract String summary();

    public String code() { return code; }
    public List<PromoCondition> conditions() { return conditions; }
    public String describe() {
        String cond = conditions.isEmpty() ? "" : " (" + conditions.stream()
                .map(PromoCondition::describe).collect(Collectors.joining("; ")) + ")";
        return code + ": " + summary() + cond;
    }

    @Override public boolean equals(Object o) { return o instanceof Promotion p && p.code.equals(code); }
    @Override public int hashCode() { return code.hashCode(); }
    @Override public String toString() { return code; }
}
