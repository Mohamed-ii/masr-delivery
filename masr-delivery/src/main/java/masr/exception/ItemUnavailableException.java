package masr.exception;

/** A menu item is switched off or not on the menu. */
public class ItemUnavailableException extends MasrException {
    private static final long serialVersionUID = 1L;
    public ItemUnavailableException(String message) { super(message); }
}
