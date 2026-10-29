package masr.cli;

import masr.domain.Order;
import masr.domain.Rider;
import masr.exception.InvalidInputException;
import masr.exception.MasrException;
import masr.report.ReportService;
import masr.service.Platform;

import java.time.Duration;
import java.util.Optional;

final class RiderArea {
    private final Platform p;
    private final Console io;

    RiderArea(Platform p, Console io) { this.p = p; this.io = io; }

    void run() {
        Rider r = login();
        if (r == null) return;
        while (true) {
            io.println("\n--- RIDER: " + r.name() + " | " + r.vehicle() + " | " + r.status() + " | " + r.district() + " ---");
            io.println("1. Go on duty   2. Go off duty   3. View assigned order   4. Mark picked up");
            io.println("5. Mark delivered   6. My statistics   0. Back");
            int choice = io.askInt("Choose an option:", 0, 6);
            if (choice == 0) return;
            try {
                switch (choice) {
                    case 1 -> {
                        p.riderService.goOnDuty(r);
                        io.println("  You are on duty." + r.activeOrder().map(o -> " Order " + o.id() + " assigned to you.").orElse(""));
                    }
                    case 2 -> { p.riderService.goOffDuty(r); io.println("  You are off duty."); }
                    case 3 -> {
                        Optional<Order> o = r.activeOrder();
                        if (o.isEmpty()) io.println("  No active order.");
                        else {
                            Views.printSummary(io, o.get());
                            io.println("  Status: " + o.get().status() + " | Pick up at " + o.get().restaurant().name()
                                    + " (" + o.get().restaurant().district() + ")");
                        }
                    }
                    case 4 -> io.println("  Order " + p.riderService.pickUp(r).id() + " is OUT_FOR_DELIVERY.");
                    case 5 -> io.println("  Order " + p.riderService.deliver(r).id() + " delivered. Well done!");
                    default -> stats(r);
                }
            } catch (MasrException | InvalidInputException e) {
                io.println("  !! " + e.getMessage());
            }
        }
    }

    private Rider login() {
        while (true) {
            Optional<String> id = io.askOptional("Rider ID (blank to go back):");
            if (id.isEmpty()) return null;
            Optional<Rider> r = p.riders.find(id.get());
            if (r.isPresent()) return r.get();
            io.println("  !! No rider with id " + id.get().toUpperCase());
        }
    }

    private void stats(Rider r) {
        ReportService.RiderStat s = p.reports.riderPerformance().stream()
                .filter(x -> x.rider().equals(r)).findFirst().orElseThrow();
        io.println("  Completed deliveries (platform history): " + s.deliveries());
        io.println("  Average delivery time: " + s.averageDuration().map(Duration::toMinutes).map(m -> m + " min").orElse("n/a (no deliveries yet)"));
    }
}
