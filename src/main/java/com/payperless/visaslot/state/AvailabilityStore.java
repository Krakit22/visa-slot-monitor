package com.payperless.visaslot.state;

import com.payperless.visaslot.model.AvailableSlot;
import java.time.Instant;
import java.util.List;

public interface AvailabilityStore {

  /**
   * Returns the slots from {@code current} that were not present in the previous successful check,
   * then updates the internal state so the next call computes against this latest snapshot.
   */
  List<AvailableSlot> diffAndStore(List<AvailableSlot> current, Instant now);

  /** Drop the remembered snapshot so the next check produces a fresh full diff. */
  void reset();
}
