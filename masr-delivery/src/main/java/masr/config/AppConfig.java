package masr.config;

import masr.common.Money;
import masr.domain.PricingConfig;
import masr.exception.ConfigurationException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Platform configuration, loaded exactly once (initialisation-on-demand holder: lazy, thread-safe,
 * lock-free). Only the composition root (Platform) and the CLI entry point call get(); domain and
 * service classes receive the values they need (e.g. PricingConfig) through their constructors,
 * so tests can build them with any configuration. See DESIGN.md, G5.
 */
public final class AppConfig {
    private static final class Holder { static final AppConfig INSTANCE = load(); }

    private final Properties props;

    private AppConfig(Properties props) { this.props = props; }

    public static AppConfig get() { return Holder.INSTANCE; }
    /** For tests: build a configuration without touching the singleton. */
    public static AppConfig of(Properties props) { return new AppConfig(props); }

    private static AppConfig load() {
        Properties p = new Properties();
        Path file = Path.of(System.getProperty("masr.config", "config/masr.properties"));
        if (Files.isRegularFile(file)) {
            try (var in = Files.newBufferedReader(file)) {
                p.load(in);
            } catch (IOException e) {
                throw new ConfigurationException("Cannot read configuration " + file + ": " + e.getMessage());
            }
        }
        return new AppConfig(p);
    }

    private String str(String key, String def) { return props.getProperty(key, def).trim(); }

    private int integer(String key, String def) {
        try { return Integer.parseInt(str(key, def)); }
        catch (NumberFormatException e) { throw new ConfigurationException("Property " + key + " must be an integer"); }
    }
    private BigDecimal decimal(String key, String def) {
        try { return new BigDecimal(str(key, def)); }
        catch (NumberFormatException e) { throw new ConfigurationException("Property " + key + " must be a number"); }
    }

    public PricingConfig pricing() {
        return new PricingConfig(Money.of(decimal("fee.delivery.base", "15")),
                Money.of(decimal("fee.delivery.perKm", "3")),
                integer("fee.delivery.includedKm", "3"),
                decimal("fee.service.percent", "10"));
    }
    public Path dataDir() { return Path.of(str("data.dir", "data")); }
    public BigDecimal defaultStock() { return decimal("stock.default", "100"); }
    public int inactiveDays() { return integer("report.inactiveDays", "30"); }
}
