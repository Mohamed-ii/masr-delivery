# Masr Delivery

    ./build.sh      # compile (works with a JDK, or a JRE that includes jdk.compiler)
    ./test.sh       # 113 self-checks, incl. the 258.00 EGP worked example
    ./run.sh        # console application

Seed customers: C01 (Bronze, Faisal), C02 (Silver), C03 (Gold), C04 (Bronze, low wallet). Restaurants R01-R07, riders D01-D04 (start off duty).
Promo codes: NILE20, WELCOME30 (first-time, min 100), FREESHIP (min 150).
Typical demo: Rider > D01 > go on duty; Customer > C01 > place order (restaurant, items, address, NILE20) > pay;
Restaurant > R02 > accept / preparing / ready (a rider is auto-assigned); Rider > pick up > delivered.
Admin > 9 loads 80 historical orders for the reports. See DESIGN.md for every design decision.
