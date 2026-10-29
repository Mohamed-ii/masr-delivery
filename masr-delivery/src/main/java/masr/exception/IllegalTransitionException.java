package masr.exception;

/** Illegal order lifecycle transition. */
public class IllegalTransitionException extends OrderStateException {
    private static final long serialVersionUID = 1L;
    public IllegalTransitionException(String message) { super(message); }
}
