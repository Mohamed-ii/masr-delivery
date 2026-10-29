package masr.cli;

import masr.common.Money;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;

/** All console I/O. Every ask* method re-prompts on invalid input instead of terminating. */
final class Console {
    static final class InputClosedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final Scanner in = new Scanner(System.in);
    private final PrintStream out = System.out;

    void println(String s) { out.println(s); }
    void println() { out.println(); }

    String ask(String prompt) {
        out.print(prompt + " ");
        out.flush();
        if (!in.hasNextLine()) throw new InputClosedException();
        return in.nextLine().trim();
    }
    int askInt(String prompt, int min, int max) {
        while (true) {
            String s = ask(prompt);
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) { }
            println("  Please enter a whole number between " + min + " and " + max + ".");
        }
    }
    String askNonBlank(String prompt) {
        while (true) {
            String s = ask(prompt);
            if (!s.isEmpty()) return s;
            println("  A value is required.");
        }
    }
    Optional<String> askOptional(String prompt) {
        String s = ask(prompt);
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }
    BigDecimal askPositiveDecimal(String prompt) {
        while (true) {
            try {
                BigDecimal v = new BigDecimal(ask(prompt));
                if (v.signum() > 0) return v;
            } catch (NumberFormatException ignored) { }
            println("  Please enter a number greater than zero.");
        }
    }
    BigDecimal askNonNegativeDecimal(String prompt) {
        while (true) {
            try {
                BigDecimal v = new BigDecimal(ask(prompt));
                if (v.signum() >= 0) return v;
            } catch (NumberFormatException ignored) { }
            println("  Please enter a number (zero or more).");
        }
    }
    Money askMoney(String prompt) { return Money.of(askPositiveDecimal(prompt)); }
    double askRating(String prompt) {
        while (true) {
            try {
                double v = Double.parseDouble(ask(prompt));
                if (v >= 0.0 && v <= 5.0) return v;
            } catch (NumberFormatException ignored) { }
            println("  Rating must be a number between 0.0 and 5.0.");
        }
    }
    boolean askYesNo(String prompt) {
        while (true) {
            String s = ask(prompt + " (y/n):").toLowerCase();
            if (s.equals("y") || s.equals("yes")) return true;
            if (s.equals("n") || s.equals("no")) return false;
            println("  Please answer y or n.");
        }
    }

    /** Numbered list; 0 = back. Empty optional means the user went back. */
    <T> Optional<T> choose(String title, List<T> options, Function<T, String> label) {
        if (options.isEmpty()) {
            println("  (nothing to choose from)");
            return Optional.empty();
        }
        println(title);
        for (int i = 0; i < options.size(); i++) println("  " + (i + 1) + ". " + label.apply(options.get(i)));
        println("  0. Back");
        int n = askInt("Choose:", 0, options.size());
        return n == 0 ? Optional.empty() : Optional.of(options.get(n - 1));
    }

    static String egp(Money m) { return m + " EGP"; }
}
