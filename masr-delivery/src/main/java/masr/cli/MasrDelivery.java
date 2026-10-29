package masr.cli;

import masr.config.AppConfig;
import masr.service.DataLoader;
import masr.service.Platform;

import java.time.Clock;

public final class MasrDelivery {
    private MasrDelivery() { }

    public static void main(String[] args) {
        AppConfig cfg = AppConfig.get();                      // the one place the singleton is touched
        Platform platform = new Platform(cfg, Clock.systemDefaultZone());
        DataLoader.load(platform, cfg.dataDir());
        AdminArea.seedPromotions(platform);
        Console io = new Console();
        try {
            run(platform, io);
        } catch (Console.InputClosedException e) {
            io.println("\nInput closed. Goodbye.");
        }
    }

    private static void run(Platform p, Console io) {
        CustomerArea customer = new CustomerArea(p, io);
        RestaurantArea restaurant = new RestaurantArea(p, io);
        RiderArea rider = new RiderArea(p, io);
        AdminArea admin = new AdminArea(p, io);
        while (true) {
            io.println("\n============================================");
            io.println("MASR DELIVERY - Main Menu");
            io.println("============================================");
            io.println("1. Customer");
            io.println("2. Restaurant");
            io.println("3. Rider");
            io.println("4. Admin & Reports");
            io.println("0. Exit");
            io.println("============================================");
            switch (io.askInt("Choose an option:", 0, 4)) {
                case 1 -> customer.run();
                case 2 -> restaurant.run();
                case 3 -> rider.run();
                case 4 -> admin.run();
                default -> { io.println("Goodbye!"); return; }
            }
        }
    }
}
