package masr.exception;

/** A promotion cannot be applied. */
public class PromotionException extends MasrException {
    private static final long serialVersionUID = 1L;
    public PromotionException(String message) { super(message); }
}
