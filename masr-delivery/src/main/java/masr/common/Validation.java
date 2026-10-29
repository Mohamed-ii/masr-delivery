package masr.common;

import masr.exception.InvalidInputException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/** Central place for the validation rules of Part H, so every entity enforces them the same way. */
public final class Validation {
    private static final Pattern MOBILE = Pattern.compile("(010|011|012|015)\\d{8}");

    private Validation() { }

    public static String nonBlank(String v, String field) {
        if (v == null || v.isBlank()) throw new InvalidInputException(field + " must not be blank");
        return v.trim();
    }
    /** Identifiers are case-insensitive: normalised to upper case. */
    public static String id(String v, String field) { return nonBlank(v, field).toUpperCase(Locale.ROOT); }

    public static String mobile(String v) {
        String s = nonBlank(v, "Mobile number");
        if (!MOBILE.matcher(s).matches())
            throw new InvalidInputException("Mobile number must be 11 digits and start with 010, 011, 012 or 015");
        return s;
    }
    public static double rating(double r) {
        if (Double.isNaN(r) || r < 0.0 || r > 5.0)
            throw new InvalidInputException("Rating must be between 0.0 and 5.0");
        return r;
    }
    public static BigDecimal positive(BigDecimal v, String field) {
        if (v == null || v.signum() <= 0) throw new InvalidInputException(field + " must be greater than zero");
        return v;
    }
    public static Money positive(Money v, String field) {
        if (v == null || v.compareTo(Money.ZERO) <= 0)
            throw new InvalidInputException(field + " must be greater than zero");
        return v;
    }
    public static int positive(int v, String field) {
        if (v <= 0) throw new InvalidInputException(field + " must be greater than zero");
        return v;
    }
    /** "fast  FOOD" and "Fast Food" are the same cuisine. */
    public static String cuisine(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String w : nonBlank(raw, "Cuisine").toLowerCase(Locale.ROOT).split("\\s+")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
