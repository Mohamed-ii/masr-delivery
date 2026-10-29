package masr.domain;

import masr.common.Validation;
import masr.exception.DuplicateEntityException;
import masr.exception.EntityNotFoundException;
import masr.exception.InvalidInputException;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Identity = id. The menu is an insertion-ordered map: ordered display and O(1) lookup by item id. */
public final class Restaurant {
    public static final Comparator<Restaurant> BY_RATING_THEN_NAME =
            Comparator.comparingDouble(Restaurant::rating).reversed()
                    .thenComparing(Restaurant::name, String.CASE_INSENSITIVE_ORDER);

    private final String id;
    private final String name;
    private final District district;
    private final Set<String> cuisines = new LinkedHashSet<>();
    private double rating;
    private boolean open;
    private final Map<String, MenuItem> menu = new LinkedHashMap<>();

    public Restaurant(String id, String name, District district, Collection<String> cuisines,
                      double rating, boolean open) {
        this.id = Validation.id(id, "Restaurant id");
        this.name = Validation.nonBlank(name, "Restaurant name");
        if (district == null) throw new InvalidInputException("District is required");
        this.district = district;
        if (cuisines == null || cuisines.isEmpty())
            throw new InvalidInputException("A restaurant needs at least one cuisine");
        cuisines.forEach(c -> this.cuisines.add(Validation.cuisine(c)));
        this.rating = Validation.rating(rating);
        this.open = open;
    }

    public String id() { return id; }
    public String name() { return name; }
    public District district() { return district; }
    public Set<String> cuisines() { return Collections.unmodifiableSet(cuisines); }
    public double rating() { return rating; }
    public void setRating(double r) { rating = Validation.rating(r); }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }

    public boolean servesCuisine(String cuisine) {
        return cuisines.stream().anyMatch(c -> c.equalsIgnoreCase(cuisine.trim()));
    }

    public void addItem(MenuItem item) throws DuplicateEntityException {
        if (!item.restaurantId().equals(id)) throw new InvalidInputException("Item belongs to another restaurant");
        if (menu.containsKey(item.id()))
            throw new DuplicateEntityException("Menu item " + item.id() + " already exists in " + name);
        menu.put(item.id(), item);
    }
    public MenuItem removeItem(String itemId) throws EntityNotFoundException {
        MenuItem removed = menu.remove(itemId.trim().toUpperCase(Locale.ROOT));
        if (removed == null) throw new EntityNotFoundException("No menu item " + itemId + " in " + name);
        return removed;
    }
    public Optional<MenuItem> findItem(String itemId) {
        return Optional.ofNullable(menu.get(itemId.trim().toUpperCase(Locale.ROOT)));
    }
    /** Immutable snapshot, in the order the items were added. */
    public List<MenuItem> menu() { return List.copyOf(menu.values()); }

    @Override public boolean equals(Object o) { return o instanceof Restaurant r && r.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return id + " " + name; }
}
