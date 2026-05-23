package com.payperless.visaslot.supersaas;

import com.payperless.visaslot.model.Appointment;
import com.payperless.visaslot.model.AvailableSlot;
import com.payperless.visaslot.model.CapacityResponse;
import com.payperless.visaslot.model.ScheduleMetadata;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the {@code app[]} array returned by SuperSaaS's {@code /ajax/capacity} into {@link
 * AvailableSlot}s the rest of the pipeline tracks.
 *
 * <p>Empirically every published slot — booked or not — is present in {@code app[]}. The JS
 * helper {@code v = i[3] - i[4]} (capacity minus booked) computes the spots-remaining counter,
 * and the cell flips to {@code full_color} when {@code i[3] <= i[4]}. We surface every published
 * slot here, free or full, and let the {@link com.payperless.visaslot.state.AvailabilityStore}
 * decide which transitions deserve a notification (first-seen, and booked→free).
 *
 * <p>Crucially this does NOT enumerate hypothetical slots from the schedule's {@code open_times}
 * window. Capacity schedules at this embassy (and most consular SuperSaaS deployments) only
 * surface days the admin has explicitly published; enumerating Mon-Fri 09:00-14:00 produced
 * hundreds of false positives whose deep-links opened cells the SuperSaaS UI rendered as closed.
 */
public class SlotDetector {

  public List<AvailableSlot> detect(
      ScheduleMetadata metadata,
      CapacityResponse capacity,
      Instant from,
      Instant to,
      java.time.ZoneId scheduleZone) {

    if (!to.isAfter(from)) {
      return List.of();
    }
    List<AvailableSlot> out = new ArrayList<>();
    for (Appointment a : capacity.appointments()) {
      if (a.capacity() <= 0) {
        continue;
      }
      if (a.to().compareTo(from) <= 0 || a.from().compareTo(to) >= 0) {
        continue;
      }
      if (overlapsAny(a.from(), a.to(), capacity.exceptions())) {
        continue;
      }
      out.add(new AvailableSlot(a.from(), a.to(), a.spotsRemaining(), a.capacity()));
    }
    out.sort(AvailableSlot::compareTo);
    return out;
  }

  private static boolean overlapsAny(Instant from, Instant to, List<Appointment> ranges) {
    for (Appointment a : ranges) {
      if (from.isBefore(a.to()) && to.isAfter(a.from())) {
        return true;
      }
    }
    return false;
  }

  public Duration totalDuration(List<AvailableSlot> slots) {
    long secs = 0;
    for (AvailableSlot s : slots) {
      secs += Duration.between(s.from(), s.to()).getSeconds();
    }
    return Duration.ofSeconds(secs);
  }
}
