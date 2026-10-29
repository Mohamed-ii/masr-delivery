package masr.service;

import masr.exception.DuplicateEntityException;
import masr.exception.EntityNotFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public final class Repository<T> {
    private final String kind;
    private final Function<T, String> idOf;
    private final Map<String, T> store = new LinkedHashMap<>();

    public Repository(String kind, Function<T, String> idOf) {
        this.kind = kind;
        this.idOf = idOf;
    }

    private static String key(String id) { return id.trim().toUpperCase(Locale.ROOT); }

    public synchronized void add(T entity) throws DuplicateEntityException {
        String k = key(idOf.apply(entity));
        if (store.containsKey(k)) throw new DuplicateEntityException(kind + " " + k + " already exists");
        store.put(k, entity);
    }
    public synchronized Optional<T> find(String id) { return Optional.ofNullable(store.get(key(id))); }
    public T get(String id) throws EntityNotFoundException {
        return find(id).orElseThrow(() -> new EntityNotFoundException("No " + kind.toLowerCase(Locale.ROOT)
                + " with id " + id.trim().toUpperCase(Locale.ROOT)));
    }
    public synchronized void remove(String id) throws EntityNotFoundException {
        T removed = store.remove(key(id));
        if (removed == null) throw new EntityNotFoundException("No " + kind.toLowerCase(Locale.ROOT) + " with id " + key(id));
    }
    public synchronized List<T> values() { return List.copyOf(store.values()); }
    public Stream<T> stream() { return values().stream(); }
    public synchronized int size() { return store.size(); }
}
