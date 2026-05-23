package com.payperless.visaslot.state;

import com.payperless.visaslot.config.MonitorProperties;
import com.payperless.visaslot.model.AvailableSlot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Process-local dedup store. Tracks the most recent free-spot count for every slot we have seen
 * and, on each call, returns the slots that warrant a fresh notification:
 *
 * <ul>
 *   <li>slots seen for the first time (free or already booked), and
 *   <li>slots whose freeSpots went from 0 → &gt;0 since the last check (a booking was released).
 * </ul>
 *
 * State is reset on a configurable cadence so a slot that stays in the snapshot across the reset
 * boundary is re-announced.
 */
@Component
public class InMemoryAvailabilityStore implements AvailabilityStore {

  private final MonitorProperties properties;
  private final Object lock = new Object();
  private Map<AvailableSlot.Key, Integer> lastSeenFreeSpots = new HashMap<>();
  private Instant lastResetAt;
  private boolean primed;

  public InMemoryAvailabilityStore(MonitorProperties properties) {
    this.properties = properties;
  }

  @Override
  public List<AvailableSlot> diffAndStore(List<AvailableSlot> current, Instant now) {
    synchronized (lock) {
      maybeReset(now);
      Map<AvailableSlot.Key, Integer> nextSnapshot = new HashMap<>(current.size());
      List<AvailableSlot> notifiable = new ArrayList<>();
      for (AvailableSlot slot : current) {
        AvailableSlot.Key key = slot.key();
        nextSnapshot.put(key, slot.freeSpots());
        if (!primed) {
          notifiable.add(slot);
          continue;
        }
        Integer previousFree = lastSeenFreeSpots.get(key);
        if (previousFree == null) {
          notifiable.add(slot);
        } else if (previousFree == 0 && slot.freeSpots() > 0) {
          notifiable.add(slot);
        }
      }
      lastSeenFreeSpots = nextSnapshot;
      primed = true;
      notifiable.sort(AvailableSlot::compareTo);
      return notifiable;
    }
  }

  @Override
  public void reset() {
    synchronized (lock) {
      lastSeenFreeSpots = new HashMap<>();
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
      lastSeenFreeSpots = new HashMap<>();
      primed = false;
      lastResetAt = now;
    }
  }
}