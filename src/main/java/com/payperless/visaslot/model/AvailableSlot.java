package com.payperless.visaslot.model;

import java.time.Instant;

public record AvailableSlot(Instant from, Instant to) implements Comparable<AvailableSlot> {

  @Override
  public int compareTo(AvailableSlot other) {
    return this.from.compareTo(other.from);
  }
}
