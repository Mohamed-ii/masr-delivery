package masr.domain;

import masr.common.Money;
import masr.common.Validation;
import masr.exception.InsufficientFundsException;
import masr.exception.InvalidInputException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Identity = id. Tier is derived from completed orders; last five searches kept in a bounded deque. */
public final class Customer {
    public static final int MAX_RECENT_SEARCHES = 5;

    private final String id;
    private final String name;
    private final String mobile;
    private final List<Address> addresses = new ArrayList<>();
    private final List<String> orderIds = new ArrayList<>();
    private final Deque<String> searches = new ArrayDeque<>();
    private Money wallet;
    private int completedOrders;

    public Customer(String id, String name, String mobile, Money wallet) {
        this.id = Validation.id(id, "Customer id");
        this.name = Validation.nonBlank(name, "Customer name");
        this.mobile = Validation.mobile(mobile);
        if (wallet == null || wallet.isNegative()) throw new InvalidInputException("Wallet balance cannot be negative");
        this.wallet = wallet;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String mobile() { return mobile; }

    public synchronized Address addAddress(District district, String detail) {
        Address a = new Address(id, district, detail);
        if (!addresses.contains(a)) addresses.add(a);
        return a;
    }
    public synchronized List<Address> addresses() { return List.copyOf(addresses); }

    public synchronized Money wallet() { return wallet; }
    public synchronized void credit(Money amount) { wallet = wallet.plus(amount); }
    public synchronized void debit(Money amount) throws InsufficientFundsException {
        if (wallet.compareTo(amount) < 0)
            throw new InsufficientFundsException("Insufficient wallet balance: need " + amount
                    + " EGP but only " + wallet + " EGP available");
        wallet = wallet.minus(amount);
    }

    public synchronized int completedOrders() { return completedOrders; }
    public synchronized void incrementCompleted() { completedOrders++; }
    public synchronized void setCompletedOrders(int n) {
        if (n < 0) throw new InvalidInputException("Completed orders cannot be negative");
        completedOrders = n;
    }
    public synchronized LoyaltyTier tier() { return LoyaltyTier.forCompleted(completedOrders); }

    public synchronized void addOrderId(String orderId) { orderIds.add(orderId); }
    public synchronized List<String> orderIds() { return List.copyOf(orderIds); }

    public synchronized void recordSearch(String text) {
        searches.addFirst(Validation.nonBlank(text, "Search text"));
        while (searches.size() > MAX_RECENT_SEARCHES) searches.removeLast();
    }
    /** Newest first, at most five. */
    public synchronized List<String> recentSearches() { return List.copyOf(searches); }

    @Override public boolean equals(Object o) { return o instanceof Customer c && c.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return id + " " + name; }
}
