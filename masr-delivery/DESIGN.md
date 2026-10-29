# Masr Delivery - Design Document

Java 17+ (built and tested on 21). No third-party libraries.
`./build.sh` compiles, `./test.sh` runs 113 self-checks (including the Part B worked example, which must equal **258.00 EGP**), `./run.sh` starts the console app.
Admin menu option 9 loads 80 deterministic historical orders so every Part D report has data to show.

Source layout: `common` (Money, Validation), `exception`, `domain` (entities, pricing, promotions, builder, factory), `service` (repositories, lifecycle/observers, dispatch, services, loader), `report`, `config`, `cli`.

Note: the brief I received jumps from Part D to Part F, and G5 refers to "Part E". I assumed E is concurrency/thread-safety and handled it as described under G5 and "Concurrency" below. Tell me if E meant something else.

---

## 1. Money

**Choice:** `Money`, an immutable value class wrapping a `long` count of piastres.

**Why:** `double` cannot represent 0.10 or 0.20 exactly (`0.1 + 0.2 != 0.3`), so totals drift and the 258.00 example could fail by a piastre. Integer piastres make `+ - x integer` exact, comparable, and hashable. Where a fraction of a piastre can arise (service fee 10%, percentage promos, per-kg pricing), the single place that does it, `Money.times(BigDecimal)` / `percent(...)`, uses `BigDecimal` with `RoundingMode.HALF_UP` ("nearest piastre"). Overflow throws (`Math.addExact`) rather than wrapping.

**Rejected:** `double` (inexact). Raw `BigDecimal` everywhere (exact, but scale/rounding decisions leak through the whole codebase and `equals` treats 2.0 and 2.00 as different). A `long` field without a type (nothing stops EGP being added to kg).

---

## 2. Data structures (Part C)

| Req | Structure | Why / rejected |
|---|---|---|
| C1 lookup by id | `Repository<T>` over `LinkedHashMap<String,T>` keyed by upper-cased id | O(1) average; ids are case-insensitive; insertion order kept for stable listings. Rejected: `ArrayList` + scan (O(n)); `TreeMap` (O(log n), sorting by id is not needed). |
| C2 menu in insertion order | `LinkedHashMap<String,MenuItem>` inside `Restaurant` | Gives both O(1) lookup by item id and deterministic order. `HashMap` iteration order is unspecified; `List` alone makes lookup O(n). |
| C3 distinct cuisines | `Set` (`LinkedHashSet` per restaurant, `TreeSet` platform-wide) | The set guarantees no duplicates and the TreeSet gives a sorted listing. Cuisine strings are normalised ("fast  FOOD" = "Fast Food") so case cannot create duplicates. |
| C4 rating desc, ties by name | `Comparator.comparingDouble(rating).reversed().thenComparing(name, CASE_INSENSITIVE_ORDER)` | A sort at listing time, not a `TreeSet` of restaurants, because ratings change and a mutated key silently corrupts a sorted set. |
| C5 dispatch queue | `PriorityQueue<Order>` with comparator: Gold-at-order-time first, then earliest READY time, then id | `poll` gives the next order in O(log n). A plain FIFO `Queue` cannot express the Gold override. Gold is **snapshotted at placement** so the ordering of queued items can never change after insertion (a mutable comparator key would corrupt the heap). The id tie-break makes ordering deterministic. Dispatch walks the queue in priority order and skips orders no eligible rider can take, so a blocked head order cannot starve others. |
| C6 last five searches | `ArrayDeque<String>`: `addFirst`, trim with `removeLast` beyond 5 | Bounded memory; newest-first is the natural iteration order. Rejected: `ArrayList` with `add(0, x)` (O(n) shifts) or keeping the full history. |
| C7 no duplicate entities | `equals`/`hashCode` per entity (below) + `Repository.add` rejecting duplicates | See below. |
| C8 no mutable exposure | Accessors return `List.copyOf` / `Collections.unmodifiableSet` snapshots; `Repository.values()` is an immutable copy | Callers get `UnsupportedOperationException` on mutation (tested). Mutation goes through owner methods (`addItem`, `addAddress`) that enforce rules. |

### What "the same entity" means (C7)

| Type | Identity | Notes |
|---|---|---|
| Restaurant, Customer, Rider, Order | their id (upper-cased) | Name, rating, wallet etc. are mutable state and are deliberately **not** in `equals`/`hashCode`, otherwise the hash would change while the object sits in a `HashSet`/`HashMap` key. |
| MenuItem | (restaurantId, itemId) | Item ids are unique per restaurant only, so the restaurant id must be part of the identity. This makes `groupingBy(item)` in the "most ordered item" report correct across restaurants. |
| Promotion | code (upper-cased) | Codes are case-insensitive. |
| Address | record: (customerId, district, detail) | A value object; including the owner means "the address belongs to this customer" is just `customer.addresses().contains(address)`. |
| Money | the piastre count | Value type. |

`equals` is by id for all entities, so two objects describing the same real-world thing collapse in sets and maps. Tested.

---

## 3. Exceptions (Part F)

Hierarchy:
```
Exception
 +- MasrException                      (checked)  catch this = "any business failure", nothing else
     +- RestaurantClosedException, ItemUnavailableException, InsufficientStockException,
        InsufficientFundsException, AddressNotOwnedException, DuplicateEntityException,
        EntityNotFoundException
     +- OrderStateException -> IllegalTransitionException
     +- PromotionException -> PromotionExpiredException, PromotionNotApplicableException
     +- RiderUnavailableException -> RiderBusyException
RuntimeException
 +- MasrRuntimeException              (unchecked)
     +- InvalidInputException   (value violates a validation rule)
     +- ConfigurationException  (bad config or seed data)
```

**The rule applied everywhere:**
* **Checked** when the failure is a *legitimate business outcome that depends on current system state*, so a perfectly correct caller still cannot rule it out in advance and must decide what to do (tell the user, retry, pick another rider). The compiler forces handling. Closed restaurant, unavailable item, empty wallet, illegal transition, expired promotion, busy rider and stock shortage all fit: state can change between "looking" and "doing".
* **Unchecked** when the failure means the *input or configuration is wrong in a way the caller could have checked up front*: a mobile number with 10 digits, a negative price, a rating of 5.1, a malformed config file. These are caller bugs or bad data, not outcomes. Checked exceptions here would clutter every constructor and stream lambda (constructors validate through `Validation`).

Both families have their own root, so `catch (MasrException | InvalidInputException e)` handles all platform problems in the console without swallowing `NullPointerException`, `OutOfMemoryError` or other JVM faults.

---

## 4. Design patterns (Part G)

**G1 Promotions: Strategy + Template Method.** `Promotion` is an abstract class: the final `discountFor(ctx)` runs the promotion's `PromoCondition`s (minimum subtotal, expiry, district, first-time), then calls the type's `compute(ctx)`. `PercentagePromotion`, `FixedAmountPromotion`, `FreeDeliveryPromotion` implement it. `PricingService` calls `promo.discountFor(...)` and never inspects the type, so a new promotion type is one new subclass. Conditions are small composable objects, so new conditions do not multiply subclasses. *Rejected:* `instanceof`/`switch` on a type enum (must be edited for every new promo); a class per type-and-condition combination (explodes).
*Interpretation:* "promotion applies to the subtotal only (never to fees)" and "free delivery" seem to conflict. I resolved it as: percentage and fixed promos are computed from the subtotal only; free delivery is a discount equal to the delivery fee payable (after the loyalty benefit), so it never touches the service fee.
*Known limit:* the admin "create promotion" prompt still has a small switch on the three types, because collecting type-specific parameters is a UI concern. Pricing itself is untouched when a type is added.

**G2 Order construction: Builder.** `OrderBuilder(id, customer, restaurant, address, placedAt)` takes the mandatory parts; `addLine`, `promotion`, `notes` are optional fluent steps; `build(pricing, firstTime)` validates everything (open restaurant, owned address, item on this menu, availability, stock, quantity rules, promotion, at least one line) and returns an immutable `Order`. `build` never mutates anything, which is what makes placement atomic: `OrderService.place` then reserves stock with rollback and registers the order. Duplicate lines for the same item merge. *Rejected:* a nine-argument constructor with nulls; telescoping constructors; setters on a half-built `Order` (observable invalid state).

**G3 Item creation: Factory with registry.** `MenuItemFactory` maps the data file's `type` to a `Creator` lambda (`STANDARD`, `WEIGHTED`, `COMBO`). All type decisions are in this one class; `DataLoader` and the restaurant "add item" screen just pass an `ItemSpec`. A new kind registers one creator (tested with `DRINK`). Pricing of items is polymorphic (`MenuItem.priceFor(quantity)`) so order pricing never checks item types either. *Rejected:* `switch` in the loader and again in the UI.

**G4 Status reactions: Observer.** `OrderLifecycle` is the single route for status changes: it calls `order.advance(...)` (which throws on illegal transitions, so nothing is published for them) and then notifies `OrderStatusListener`s: `CustomerNotifier`, `RiderDashboard`, `AuditLog`, `StatisticsListener`. A fourth/fifth reaction = implement the interface and `subscribe`; `Order` and `OrderService` are unchanged. A failing listener is logged and cannot break the order flow or the other listeners. `Order` itself stays a pure state machine (the transition rule lives in `OrderStatus.canTransitionTo`). *Rejected:* the order calling four collaborators directly; inheritance-based hooks.
Trade-off: `Order.advance` is public, so code could bypass the lifecycle. Moving the lifecycle into the `Order` would couple the entity to services; I kept the entity pure and routed all service code through `OrderLifecycle`.

**G5 Configuration: Singleton, used sparingly.** `AppConfig` is an initialisation-on-demand-holder singleton: loaded once, lazily, thread-safe without locking. It is touched in exactly one place (`MasrDelivery.main`), which converts it into plain objects (`PricingConfig`, data dir, default stock) that are passed to the composition root `Platform`. That gives "reachable from anywhere" *where genuinely needed* without a global being read deep inside business code.
*The contention:* a singleton is hidden global state. Domain code that calls `AppConfig.get()` is untestable with different fees (the worked-example test builds `PricingConfig` directly), cannot run two differently-configured platforms in one JVM, and interacts badly with concurrency (Part E): if the config were mutable, every thread would share one mutable object. So the singleton is immutable after load and confined to the entry point; everything else gets values by constructor. *Rejected:* a static mutable config read everywhere; passing a config object through every constructor ("telescoping DI") - the composition root avoids that by handing each service only the slice it needs.

**G6 Rider behaviour: Strategy.** `VehicleType` (`maxRangeKm`, `speedKmh`, `maxOrderItems`) with `Motorcycle`, `Bicycle`, `Car`. `Rider` holds a `VehicleType` and `canServe(order)` consults it. `VehicleTypes` is a registry; adding an electric scooter is one new class plus `register` (tested), with no change to `Rider` or the dispatcher. *Rejected:* an `enum` with `switch` in `Rider` (each new vehicle edits it); subclassing `Rider` per vehicle (a rider's vehicle is a property, not a different kind of rider).

---

## 5. The one-active-order invariant and concurrency

* `Rider.assign(order)` checks `activeOrder != null` / status and sets both **inside one `synchronized` method**. It is the only way an order is put on a rider, so two threads cannot both win. A 50-thread race test asserts exactly one assignment succeeds.
* `DispatchService.dispatch()` is synchronized, picks only `isAvailable()` riders and treats `RiderUnavailableException` from `assign` as "someone else got them, try the next rider".
* `cancel` releases the rider; `deliver` clears it; `goOffDuty` is refused while an order is held.
* Other shared mutable state (`Repository`, `Customer` wallet, `MenuItem` stock) uses `synchronized` per object. The console is single-threaded, but the invariants do not depend on that.

## 6. Reports (Part D)

All ten are `Stream` pipelines in `ReportService` (`groupingBy`, `summarizingLong`, `reducing`, `flatMap`, `Optional`), not accumulation loops. Definitions I chose: *revenue* = totals of DELIVERED orders; *average order value* and *total spent* exclude CANCELLED; *delivery duration* = pickup (OUT_FOR_DELIVERY) to DELIVERED; *most ordered* counts order lines of non-cancelled orders. Reports 7 and 9 return `Optional`, which the console renders as an honest "no orders yet" message (tested on an empty platform). Average order value is exact (sum / count with `HALF_UP`), not `averagingDouble`. All dates use `java.time` (`LocalDateTime`, `YearMonth`, `Clock`); a fixed `Clock` is injected so time-based behaviour is testable.

Search (`RestaurantFilters.search(source, Predicate)`) takes the caller's predicate; the provided factories (`inDistrict`, `cuisine`, `minRating`, `priceCeiling`, `textMatch`) combine with `and`/`or`, and any lambda works, so new criteria never change the search code.

## 7. Assumptions

* District-to-district distances are a fixed whole-km table (`District`); Faisal-Maadi is 12 km, matching the worked example.
* Delivery fee extra km = `max(0, km - 3)` times 3 EGP.
* Stock is per item (kg for weighted items); a combo consumes its own stock, not its parts'. Cancelling restores stock.
* Payment is a separate step ("Pay from wallet"); cancelling a paid order refunds the full total, an unpaid one refunds nothing.
* A rider's vehicle limits: motorcycle 25 km / 6 lines, bicycle 6 km / 3 lines, car 40 km / 12 lines (adjustable in the vehicle classes). The dispatcher picks the nearest eligible available rider.
* "First-time customer" = has no earlier non-cancelled order.
* Loyalty: seed customers carry a starting completed-order count (tier is still derived, never stored).
