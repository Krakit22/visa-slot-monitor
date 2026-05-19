package com.payperless.visaslot.monitor;

import com.payperless.visaslot.config.MonitorProperties;
import com.payperless.visaslot.config.SuperSaasProperties;
import com.payperless.visaslot.model.AvailableSlot;
import com.payperless.visaslot.model.CapacityResponse;
import com.payperless.visaslot.model.ScheduleMetadata;
import com.payperless.visaslot.state.AvailabilityStore;
import com.payperless.visaslot.supersaas.SlotDetector;
import com.payperless.visaslot.supersaas.SuperSaasClient;
import com.payperless.visaslot.telegram.TelegramNotifier;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one full check cycle: ensure metadata is fresh, fetch the capacity JSON for the
 * configured lookahead, ask {@link SlotDetector} for currently-available slots, run them through
 * the {@link AvailabilityStore} for dedup, and hand any newly-available slots to the {@link
 * TelegramNotifier}.
 */
@Service
public class SlotMonitorService {

  private static final Logger log = LoggerFactory.getLogger(SlotMonitorService.class);

  private final SuperSaasClient client;
  private final SlotDetector detector;
  private final AvailabilityStore store;
  private final TelegramNotifier notifier;
  private final MonitorProperties monitorProperties;
  private final SuperSaasProperties superSaasProperties;
  private final Clock clock;

  private volatile ScheduleMetadata cachedMetadata;

  public SlotMonitorService(
      SuperSaasClient client,
      SlotDetector detector,
      AvailabilityStore store,
      TelegramNotifier notifier,
      MonitorProperties monitorProperties,
      SuperSaasProperties superSaasProperties,
      Clock clock) {
    this.client = client;
    this.detector = detector;
    this.store = store;
    this.notifier = notifier;
    this.monitorProperties = monitorProperties;
    this.superSaasProperties = superSaasProperties;
    this.clock = clock;
  }

  public CheckOutcome checkOnce() {
    Instant now = clock.instant();
    log.info("Slot check tick at {}", now);

    ScheduleMetadata metadata;
    try {
      metadata = ensureMetadata(now);
    } catch (Exception e) {
      log.warn("Failed to obtain schedule metadata: {}", e.toString());
      return CheckOutcome.metadataFailure(e);
    }

    Instant from = now;
    Instant to = now.plus(monitorProperties.lookahead());

    CapacityResponse capacity;
    try {
      capacity = client.fetchCapacity(metadata, from, to);
    } catch (Exception e) {
      log.warn("Failed to fetch capacity JSON: {}", e.toString());
      return CheckOutcome.fetchFailure(e);
    }

    List<AvailableSlot> available =
        detector.detect(metadata, capacity, from, to, monitorProperties.scheduleZone());
    log.info(
        "Detected {} available slots in window [{} → {}] (bookings={}, exceptions={})",
        available.size(),
        from,
        to,
        capacity.appointments().size(),
        capacity.exceptions().size());

    List<AvailableSlot> newSlots = store.diffAndStore(available, now);
    if (newSlots.isEmpty()) {
      log.debug("No newly-available slots since last check");
      return CheckOutcome.noChange(available.size());
    }

    log.info("{} newly-available slot(s); notifying", newSlots.size());
    try {
      notifier.notifyAvailable(newSlots, superSaasProperties.bookingUrl(), now);
    } catch (Exception e) {
      log.error("Notification failed; will retry next cycle", e);
      return CheckOutcome.notifyFailure(newSlots.size(), e);
    }
    return CheckOutcome.notified(newSlots.size());
  }

  private ScheduleMetadata ensureMetadata(Instant now) {
    ScheduleMetadata current = cachedMetadata;
    if (current != null && !current.isExpired(now, superSaasProperties.metadataTtl())) {
      return current;
    }
    ScheduleMetadata fresh = client.fetchScheduleMetadata();
    cachedMetadata = fresh;
    log.info(
        "Refreshed schedule metadata: rp_id={}, token={}, slot_length_sec={}",
        fresh.rpId(),
        fresh.token(),
        fresh.slotLengthSeconds());
    return fresh;
  }

  public sealed interface CheckOutcome {
    static CheckOutcome metadataFailure(Throwable t) {
      return new Failure(Phase.METADATA, t);
    }

    static CheckOutcome fetchFailure(Throwable t) {
      return new Failure(Phase.FETCH, t);
    }

    static CheckOutcome notifyFailure(int newSlotCount, Throwable t) {
      return new Failure(Phase.NOTIFY, t, newSlotCount);
    }

    static CheckOutcome noChange(int availableCount) {
      return new NoChange(availableCount);
    }

    static CheckOutcome notified(int newSlotCount) {
      return new Notified(newSlotCount);
    }

    enum Phase {
      METADATA,
      FETCH,
      NOTIFY
    }

    record Failure(Phase phase, Throwable cause, int newSlotCount) implements CheckOutcome {
      public Failure(Phase phase, Throwable cause) {
        this(phase, cause, 0);
      }
    }

    record NoChange(int availableCount) implements CheckOutcome {}

    record Notified(int newSlotCount) implements CheckOutcome {}
  }
}
