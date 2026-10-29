package masr.exception;

/** Base of every recoverable (checked) platform failure: catch this to catch 'any business failure' and nothing else. */
public class MasrException extends Exception {
    private static final long serialVersionUID = 1L;
    public MasrException(String message) { super(message); }
}
