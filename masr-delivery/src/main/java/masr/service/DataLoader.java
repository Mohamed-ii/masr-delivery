package masr.service;

import masr.common.Money;
import masr.domain.Customer;
import masr.domain.District;
import masr.domain.MenuItem;
import masr.domain.MenuItemFactory.ItemSpec;
import masr.domain.Restaurant;
import masr.domain.Rider;
import masr.domain.VehicleTypes;
import masr.exception.ConfigurationException;
import masr.exception.MasrException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Reads the CSV seed files. Item creation goes through the factory: no type logic here. */
public final class DataLoader {
    private DataLoader() { }

    private static List<String[]> rows(Path file) {
        if (!Files.isRegularFile(file)) return List.of();
        try {
            return Files.readAllLines(file).stream()
                    .map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(l -> Arrays.stream(l.split(",", -1)).map(String::trim).toArray(String[]::new))
                    .toList();
        } catch (IOException e) {
            throw new ConfigurationException("Cannot read " + file + ": " + e.getMessage());
        }
    }

    public static void load(Platform p, Path dir) {
        try {
            for (String[] c : rows(dir.resolve("restaurants.csv")))
                p.restaurants.add(new Restaurant(c[0], c[1], District.parse(c[2]),
                        Arrays.asList(c[3].split("\\|")), Double.parseDouble(c[4]), Boolean.parseBoolean(c[5])));

            for (String[] c : rows(dir.resolve("menu.csv"))) {
                Restaurant r = p.restaurants.get(c[0]);
                ItemSpec spec = new ItemSpec(c[0], c[1], c[2], c[3], Money.of(c[4]), c[5],
                        Integer.parseInt(c[6]), Boolean.parseBoolean(c[7]), c.length > 8 ? c[8] : "");
                MenuItem item = p.itemFactory.create(spec, id -> r.findItem(id).orElse(null), p.config.defaultStock());
                r.addItem(item);
            }
            for (String[] c : rows(dir.resolve("customers.csv"))) {
                Customer cu = new Customer(c[0], c[1], c[2], Money.of(c[3]));
                cu.setCompletedOrders(Integer.parseInt(c[4]));
                for (String a : c[5].split("\\|")) {
                    String[] parts = a.split(":", 2);
                    cu.addAddress(District.parse(parts[0]), parts[1]);
                }
                p.customers.add(cu);
            }
            for (String[] c : rows(dir.resolve("riders.csv")))
                p.riders.add(new Rider(c[0], c[1], VehicleTypes.parse(c[2]), District.parse(c[3])));
        } catch (MasrException | RuntimeException e) {
            throw new ConfigurationException("Bad seed data: " + e.getMessage());
        }
    }
}
