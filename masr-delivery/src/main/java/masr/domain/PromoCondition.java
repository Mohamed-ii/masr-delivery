package masr.domain;

import masr.exception.PromotionException;

/** A rule a promotion may carry. Conditions compose: a promotion holds any number of them. */
public interface PromoCondition {
    void check(PromoContext ctx) throws PromotionException;
    String describe();
}
