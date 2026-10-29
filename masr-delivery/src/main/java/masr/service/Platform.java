package masr.service;

import masr.config.AppConfig;
import masr.domain.*;
import masr.report.ReportService;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

/**
 * Composition root: the one place where everything is constructed and wired. The fields are public
 * final on purpose - this is a wiring holder, not a domain object.
 */
public final class Platform {
    public final AppConfig config;
    public final Clock clock;
    public final Repository<Restaurant> restaurants = new Repository<>("Restaurant", Restaurant::id);
    public final Repository<Customer> customers = new Repository<>("Customer", Customer::id);
    public final Repository<Rider> riders = new Repository<>("Rider", Rider::id);
    public final Repository<Order> orders = new Repository<>("Order", Order::id);
    public final Repository<Promotion> promotions = new Repository<>("Promotion", Promotion::code);
    public final MenuItemFactory itemFactory = MenuItemFactory.withDefaults();
    public final PricingService pricing;
    public final OrderLifecycle lifecycle;
    public final CustomerNotifier notifier = new CustomerNotifier();
    public final RiderDashboard dashboard = new RiderDashboard();
    public final AuditLog audit = new AuditLog();
    public final StatisticsListener stats = new StatisticsListener();
    public final DispatchService dispatch;
    public final OrderService orderService;
    public final RiderService riderService;
    public final ReportService reports;

    public Platform(AppConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
        this.pricing = new PricingService(config.pricing());
        this.lifecycle = new OrderLifecycle(clock);
        lifecycle.subscribe(notifier);
        lifecycle.subscribe(dashboard);
        lifecycle.subscribe(audit);
        lifecycle.subscribe(stats);
        this.dispatch = new DispatchService(riders, lifecycle);
        this.orderService = new OrderService(orders, riders, pricing, lifecycle, dispatch, clock);
        this.riderService = new RiderService(lifecycle, dispatch);
        this.reports = new ReportService(orders, restaurants, riders, customers, clock, config.inactiveDays());
    }

    public Optional<Promotion> findPromotion(String code) {
        return promotions.find(code.trim().toUpperCase(Locale.ROOT));
    }
}
