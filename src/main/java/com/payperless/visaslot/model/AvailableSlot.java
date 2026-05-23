package com.payperless.visaslot.model;

import java.time.Instant;

public record AvailableSlot(Instant from, Instant to, int freeSpots, int capacity)
    implements Comparable<AvailableSlot> {

  public AvailableSlot(Instant from, Instant to) {
    this(from, to, 0, 0);
  }

  public boolean isFree() {
    return freeSpots > 0;
  }

  public Key key() {
    return new Key(from, to);
  }

  @Override
  public int compareTo(AvailableSlot other) {
    return this.from.compareTo(other.from);
  }

  public record Key(Instant from, Instant to) {}
}