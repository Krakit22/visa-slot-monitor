package com.payperless.visaslot.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.payperless.visaslot.config.MonitorProperties;
import com.payperless.visaslot.model.AvailableSlot;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryAvailabilityStoreTest {

  private static final Instant T0 = Instant.parse("2026-05-19T10:00:00Z");

  private final MonitorProperties props =
      new MonitorProperties(
          Duration.ofMinutes(5), Duration.ofDays(30), ZoneId.of("Asia/Nicosia"), Duration.ofHours(24));
  private final InMemoryAvailabilityStore store = new InMemoryAvailabilityStore(props);

  @Test
  void firstCallReturnsEverything() {
    List<AvailableSlot> slots = List.of(free(0), free(30));
    List<AvailableSlot> diff = store.diffAndStore(slots, T0);
    assertThat(diff).containsExactly(free(0), free(30));
  }

  @Test
  void secondCallWithSameSetReturnsNothing() {
    List<AvailableSlot> slots = List.of(free(0), free(30));
    store.diffAndStore(slots, T0);
    List<AvailableSlot> diff = store.diffAndStore(slots, T0.plusSeconds(60));
    assertThat(diff).isEmpty();
  }

  @Test
  void onlyNewSlotsAreReturned() {
    store.diffAndStore(List.of(free(0)), T0);
    List<AvailableSlot> diff =
        store.diffAndStore(List.of(free(0), free(30), free(60)), T0.plusSeconds(60));
    assertThat(diff).containsExactly(free(30), free(60));
  }

  @Test
  void slotsThatDisappearDoNotTrigger() {
    store.diffAndStore(List.of(free(0), free(30)), T0);
    List<AvailableSlot> diff = store.diffAndStore(List.of(free(0)), T0.plusSeconds(60));
    assertThat(diff).isEmpty();
  }

  @Test
  void firstSightingOfABookedSlotStillNotifies() {
    List<AvailableSlot> diff = store.diffAndStore(List.of(booked(0)), T0);
    assertThat(diff).containsExactly(booked(0));
  }

  @Test
  void bookedToFreeTransitionReNotifies() {
    store.diffAndStore(List.of(booked(0)), T0);
    List<AvailableSlot> diff = store.diffAndStore(List.of(free(0)), T0.plusSeconds(60));
    assertThat(diff).containsExactly(free(0));
  }

  @Test
  void freeToBookedTransitionIsQuiet() {
    store.diffAndStore(List.of(free(0)), T0);
    List<AvailableSlot> diff = store.diffAndStore(List.of(booked(0)), T0.plusSeconds(60));
    assertThat(diff).isEmpty();
  }

  @Test
  void freeSpotsCountChangingButStayingPositiveIsQuiet() {
    store.diffAndStore(List.of(multi(0, 3, 5)), T0);
    List<AvailableSlot> diff = store.diffAndStore(List.of(multi(0, 1, 5)), T0.plusSeconds(60));
    assertThat(diff).isEmpty();
  }

  @Test
  void resetAfterDedupResetIntervalReplaysFullSnapshot() {
    store.diffAndStore(List.of(free(0), free(30)), T0);
    Instant later = T0.plus(props.dedupReset()).plusSeconds(1);
    List<AvailableSlot> diff = store.diffAndStore(List.of(free(0), free(30)), later);
    assertThat(diff).containsExactly(free(0), free(30));
  }

  @Test
  void explicitResetReplaysFullSnapshot() {
    store.diffAndStore(List.of(free(0), free(30)), T0);
    store.reset();
    List<AvailableSlot> diff = store.diffAndStore(List.of(free(0), free(30)), T0.plusSeconds(60));
    assertThat(diff).containsExactly(free(0), free(30));
  }

  private static AvailableSlot free(int offsetMinutes) {
    return multi(offsetMinutes, 1, 1);
  }

  private static AvailableSlot booked(int offsetMinutes) {
    return multi(offsetMinutes, 0, 1);
  }

  private static AvailableSlot multi(int offsetMinutes, int freeSpots, int capacity) {
    Instant from = Instant.parse("2026-05-20T09:00:00Z").plusSeconds(offsetMinutes * 60L);
    return new AvailableSlot(from, from.plusSeconds(1800), freeSpots, capacity);
  }
}