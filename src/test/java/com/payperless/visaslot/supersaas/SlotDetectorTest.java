package com.payperless.visaslot.supersaas;

import static org.assertj.core.api.Assertions.assertThat;

import com.payperless.visaslot.model.Appointment;
import com.payperless.visaslot.model.AvailableSlot;
import com.payperless.visaslot.model.CapacityResponse;
import com.payperless.visaslot.model.ScheduleMetadata;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotDetectorTest {

  private static final ZoneId NICOSIA = ZoneId.of("Asia/Nicosia");
  private static final List<Integer> EMBASSY_OPEN_TIMES =
      List.of(0, 540, 540, 540, 540, 540, 0, 1440, 840, 840, 840, 840, 840, 1440);

  private final SlotDetector detector = new SlotDetector();

  @Test
  void noAppointmentsMeansNothingAvailable() {
    Instant from = Instant.parse("2026-05-18T00:00:00Z");
    Instant to = Instant.parse("2026-05-25T00:00:00Z");
    assertThat(detector.detect(metadata(1800), CapacityResponse.empty(), from, to, NICOSIA))
        .isEmpty();
  }

  @Test
  void allBookedSlotsAreFilteredOut() {
    // Embassy-shaped data: each entry has capacity == booked == 1 (fully taken).
    Instant slotFrom = Instant.parse("2026-06-09T10:00:00Z");
    Instant slotTo = slotFrom.plusSeconds(1800);
    CapacityResponse capacity =
        new CapacityResponse(List.of(new Appointment(slotFrom, slotTo, 72059434L, 1, 1)), List.of());

    Instant from = Instant.parse("2026-05-18T00:00:00Z");
    Instant to = Instant.parse("2026-07-01T00:00:00Z");

    assertThat(detector.detect(metadata(1800), capacity, from, to, NICOSIA)).isEmpty();
  }

  @Test
  void slotWithFreeCapacityIsReturned() {
    Instant slotFrom = Instant.parse("2026-06-09T06:00:00Z");
    Instant slotTo = slotFrom.plusSeconds(1800);
    CapacityResponse capacity =
        new CapacityResponse(List.of(new Appointment(slotFrom, slotTo, 1L, 1, 0)), List.of());

    List<AvailableSlot> slots =
        detector.detect(
            metadata(1800),
            capacity,
            Instant.parse("2026-05-18T00:00:00Z"),
            Instant.parse("2026-07-01T00:00:00Z"),
            NICOSIA);

    assertThat(slots).containsExactly(new AvailableSlot(slotFrom, slotTo));
  }

  @Test
  void partiallyFreeMultiCapacitySlotIsReturned() {
    Instant slotFrom = Instant.parse("2026-06-09T06:00:00Z");
    Instant slotTo = slotFrom.plusSeconds(1800);
    CapacityResponse capacity =
        new CapacityResponse(List.of(new Appointment(slotFrom, slotTo, 1L, 5, 3)), List.of());

    List<AvailableSlot> slots =
        detector.detect(
            metadata(1800),
            capacity,
            Instant.parse("2026-05-18T00:00:00Z"),
            Instant.parse("2026-07-01T00:00:00Z"),
            NICOSIA);

    assertThat(slots).hasSize(1);
  }

  @Test
  void slotOverlappingExceptionIsSuppressed() {
    Instant slotFrom = Instant.parse("2026-06-01T06:00:00Z");
    Instant slotTo = slotFrom.plusSeconds(1800);
    Appointment free = new Appointment(slotFrom, slotTo, 1L, 1, 0);
    Appointment exc =
        new Appointment(
            Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T23:59:00Z"));

    CapacityResponse capacity = new CapacityResponse(List.of(free), List.of(exc));

    assertThat(
            detector.detect(
                metadata(1800),
                capacity,
                Instant.parse("2026-05-18T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"),
                NICOSIA))
        .isEmpty();
  }

  @Test
  void slotsOutsideWindowAreFiltered() {
    Instant before = Instant.parse("2026-04-30T06:00:00Z");
    Instant after = Instant.parse("2026-08-01T06:00:00Z");
    CapacityResponse capacity =
        new CapacityResponse(
            List.of(
                new Appointment(before, before.plusSeconds(1800), 1L, 1, 0),
                new Appointment(after, after.plusSeconds(1800), 2L, 1, 0)),
            List.of());

    assertThat(
            detector.detect(
                metadata(1800),
                capacity,
                Instant.parse("2026-05-18T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"),
                NICOSIA))
        .isEmpty();
  }

  @Test
  void mixedFreeAndBookedAreFilteredCorrectly() {
    Instant t1 = Instant.parse("2026-06-09T06:00:00Z");
    Instant t2 = Instant.parse("2026-06-09T06:30:00Z");
    Instant t3 = Instant.parse("2026-06-09T07:00:00Z");
    CapacityResponse capacity =
        new CapacityResponse(
            List.of(
                new Appointment(t1, t1.plusSeconds(1800), 1L, 1, 1), // booked
                new Appointment(t2, t2.plusSeconds(1800), 2L, 1, 0), // free
                new Appointment(t3, t3.plusSeconds(1800), 3L, 1, 1)), // booked
            List.of());

    List<AvailableSlot> slots =
        detector.detect(
            metadata(1800),
            capacity,
            Instant.parse("2026-05-18T00:00:00Z"),
            Instant.parse("2026-07-01T00:00:00Z"),
            NICOSIA);

    assertThat(slots).containsExactly(new AvailableSlot(t2, t2.plusSeconds(1800)));
  }

  @Test
  void emptyWindowProducesNothing() {
    Instant from = Instant.parse("2026-05-18T00:00:00Z");
    assertThat(detector.detect(metadata(1800), CapacityResponse.empty(), from, from, NICOSIA))
        .isEmpty();
  }

  private static ScheduleMetadata metadata(int slotLengthSec) {
    return new ScheduleMetadata(
        782348L, 1367387L, EMBASSY_OPEN_TIMES, slotLengthSec, Instant.parse("2026-05-19T12:00:00Z"));
  }
}
