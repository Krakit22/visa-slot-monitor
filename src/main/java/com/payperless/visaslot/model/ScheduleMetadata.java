package com.payperless.visaslot.model;

import java.time.Instant;
import java.util.List;

/**
 * Metadata parsed out of the inline JS on the SuperSaaS schedule page.
 *
 * <p>{@code openTimes} is a 14-element array: indices 0..6 are minute-of-day for "open" on
 * Sun..Sat, indices 7..13 are minute-of-day for "close" on the same days. 0 in both means the
 * day is closed. 1440 ({@code minutesPerDay}) means 24h.
 */
public record ScheduleMetadata(
    long rpId, long token, List<Integer> openTimes, int slotLengthSeconds, Instant fetchedAt) {

  public boolean isExpired(Instant now, java.time.Duration ttl) {
    return fetchedAt.plus(ttl).isBefore(now);
  }
}
