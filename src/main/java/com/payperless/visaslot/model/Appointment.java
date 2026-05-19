package com.payperless.visaslot.model;

import java.time.Instant;

/**
 * One {@code app[]} entry returned by SuperSaaS's {@code /ajax/capacity}. SuperSaaS exposes each
 * published slot — booked or not — through this array; field positions are documented from {@code
 * capacity.js}:
 *
 * <ul>
 *   <li>{@code i[0]} → from (epoch seconds)
 *   <li>{@code i[1]} → to (epoch seconds)
 *   <li>{@code i[2]} → slot id (database PK)
 *   <li>{@code i[3]} → total capacity
 *   <li>{@code i[4]} → booked count; JS uses {@code i[3] - i[4]} to render the spots-remaining
 *       counter, and the cell flips to "full" colour when {@code i[3] <= i[4]}.
 * </ul>
 *
 * Entries used as exceptions ({@code exc[]}) reuse the first two fields and leave the rest at
 * their zero defaults; constructor with only {@code from} / {@code to} models that case.
 */
public record Appointment(Instant from, Instant to, long slotId, int capacity, int booked) {

  public Appointment(Instant from, Instant to) {
    this(from, to, 0L, 0, 0);
  }

  public boolean hasFreeSpot() {
    return capacity > 0 && booked < capacity;
  }

  public int spotsRemaining() {
    return Math.max(0, capacity - booked);
  }
}
