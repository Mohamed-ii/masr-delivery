package masr.exception;

/** A promotion condition is not met. */
public class PromotionNotApplicableException extends PromotionException {
    private static final long serialVersionUID = 1L;
    public PromotionNotApplicableException(String message) { super(message); }
}
