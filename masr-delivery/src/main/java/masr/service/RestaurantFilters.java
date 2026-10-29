package masr.service;

import masr.common.Money;
import masr.domain.District;
import masr.domain.MenuItem;
import masr.domain.Restaurant;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Search is "caller supplies the condition": RestaurantFilters.search() never changes. These are just
 * ready-made Predicate factories; combine with and()/or(), or pass any lambda.
 */
public final class RestaurantFilters {
    private RestaurantFilters() { }

    public static List<Restaurant> search(Collection<Restaurant> source, Predicate<Restaurant> condition) {
        return source.stream().filter(condition).sorted(Restaurant.BY_RATING_THEN_NAME).toList();
    }
    public static Predicate<Restaurant> open() { return Restaurant::isOpen; }
    public static Predicate<Restaurant> inDistrict(District d) { return r -> r.district() == d; }
    public static Predicate<Restaurant> cuisine(String c) { return r -> r.servesCuisine(c); }
    public static Predicate<Restaurant> minRating(double min) { return r -> r.rating() >= min; }
    /** At least one available item at or below the ceiling. */
    public static Predicate<Restaurant> priceCeiling(Money max) {
        return r -> r.menu().stream().filter(MenuItem::isAvailable).anyMatch(i -> i.price().compareTo(max) <= 0);
    }
    public static Predicate<Restaurant> textMatch(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        return r -> r.name().toLowerCase(Locale.ROOT).contains(t)
                || r.cuisines().stream().anyMatch(c -> c.toLowerCase(Locale.ROOT).contains(t));
    }
}
