package masr.exception;

/** The promotion has expired. */
public class PromotionExpiredException extends PromotionException {
    private static final long serialVersionUID = 1L;
    public PromotionExpiredException(String message) { super(message); }
}
