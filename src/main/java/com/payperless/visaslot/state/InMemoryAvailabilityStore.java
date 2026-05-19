package com.payperless.visaslot.state;

import com.payperless.visaslot.config.MonitorProperties;
import com.payperless.visaslot.model.AvailableSlot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Process-local dedup store. Holds the most recent set of {@link AvailableSlot} fingerprints and,
 * on each call, returns the elements of the new snapshot that were absent last time. State is
 * reset on a configurable cadence so a slot that stays available across the reset boundary is
 * re-announced.
 */
@Component
public class InMemoryAvailabilityStore implements AvailabilityStore {

  private final MonitorProperties properties;
  private final Object lock = new Object();
  private Set<AvailableSlot> lastSnapshot = new HashSet<>();
  private Instant lastResetAt;
  private boolean primed;

  public InMemoryAvailabilityStore(MonitorProperties properties) {
    this.properties = properties;
  }

  @Override
  public List<AvailableSlot> diffAndStore(List<AvailableSlot> current, Instant now) {
    synchronized (lock) {
      maybeReset(now);
      Set<AvailableSlot> currentSet = new HashSet<>(current);
      List<AvailableSlot> newlyAvailable;
      if (!primed) {
        newlyAvailable = new ArrayList<>(current);
        primed = true;
      } else {
        newlyAvailable = new ArrayList<>();
        for (AvailableSlot slot : current) {
          if (!lastSnapshot.contains(slot)) {
            newlyAvailable.add(slot);
          }
        }
      }
      lastSnapshot = currentSet;
      newlyAvailable.sort(AvailableSlot::compareTo);
      return newlyAvailable;
    }
  }

  @Override
  public void reset() {
    synchronized (lock) {
      lastSnapshot = new HashSet<>();
      primed = false;
      lastResetAt = null;
    }
  }

  private void maybeReset(Instant now) {
    if (lastResetAt == null) {
      lastResetAt = now;
      return;
    }
    if (lastResetAt.plus(properties.dedupReset()).isBefore(now)) {
      lastSnapshot = new HashSet<>();
      primed = false;
      lastResetAt = now;
    }
  }
}
