package com.payperless.visaslot.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SlotMonitorServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-19T08:00:00Z");

  private final SuperSaasClient client = Mockito.mock(SuperSaasClient.class);
  private final SlotDetector detector = Mockito.mock(SlotDetector.class);
  private final AvailabilityStore store = Mockito.mock(AvailabilityStore.class);
  private final TelegramNotifier notifier = Mockito.mock(TelegramNotifier.class);
  private final MonitorProperties monitorProps =
      new MonitorProperties(
          Duration.ofMinutes(5), Duration.ofDays(30), ZoneId.of("Asia/Nicosia"), Duration.ofHours(24));
  private final SuperSaasProperties superSaasProps =
      new SuperSaasProperties(
          "https://www.supersaas.com",
          "/schedule/EmbassyofGreeceinCyprus/VISA",
          "https://www.supersaas.com/schedule/EmbassyofGreeceinCyprus/VISA",
          Duration.ofHours(6),
          "TestAgent/1.0",
          Duration.ofSeconds(5),
          Duration.ofSeconds(15));
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private SlotMonitorService service;

  @BeforeEach
  void setUp() {
    service =
        new SlotMonitorService(
            client, detector, store, notifier, monitorProps, superSaasProps, clock);
  }

  @Test
  void notifiesOnNewSlots() {
    ScheduleMetadata metadata =
        new ScheduleMetadata(
            1L, 2L, List.of(0, 540, 540, 540, 540, 540, 0, 1440, 840, 840, 840, 840, 840, 1440), 1800, NOW);
    when(client.fetchScheduleMetadata()).thenReturn(metadata);
    when(client.fetchCapacity(eq(metadata), any(), any())).thenReturn(CapacityResponse.empty());
    AvailableSlot slot =
        new AvailableSlot(Instant.parse("2026-05-20T06:00:00Z"), Instant.parse("2026-05-20T06:30:00Z"));
    when(detector.detect(any(), any(), any(), any(), any())).thenReturn(List.of(slot));
    when(store.diffAndStore(anyList(), any())).thenReturn(List.of(slot));

    SlotMonitorService.CheckOutcome outcome = service.checkOnce();

    assertThat(outcome).isInstanceOf(SlotMonitorService.CheckOutcome.Notified.class);
    verify(notifier).notifyAvailable(eq(List.of(slot)), eq(superSaasProps.bookingUrl()), eq(NOW));
  }

  @Test
  void quietWhenStoreReturnsEmpty() {
    ScheduleMetadata metadata =
        new ScheduleMetadata(1L, 2L, anyOpenTimes(), 1800, NOW);
    when(client.fetchScheduleMetadata()).thenReturn(metadata);
    when(client.fetchCapacity(any(), any(), any())).thenReturn(CapacityResponse.empty());
    when(detector.detect(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(store.diffAndStore(anyList(), any())).thenReturn(List.of());

    SlotMonitorService.CheckOutcome outcome = service.checkOnce();

    assertThat(outcome).isInstanceOf(SlotMonitorService.CheckOutcome.NoChange.class);
    verify(notifier, never()).notifyAvailable(any(), any(), any());
  }

  @Test
  void metadataFailureSkipsCapacityCall() {
    when(client.fetchScheduleMetadata()).thenThrow(new RuntimeException("boom"));

    SlotMonitorService.CheckOutcome outcome = service.checkOnce();

    assertThat(outcome).isInstanceOf(SlotMonitorService.CheckOutcome.Failure.class);
    verify(client, never()).fetchCapacity(any(), any(), any());
    verify(notifier, never()).notifyAvailable(any(), any(), any());
  }

  @Test
  void metadataIsCachedBetweenTicks() {
    ScheduleMetadata metadata = new ScheduleMetadata(1L, 2L, anyOpenTimes(), 1800, NOW);
    when(client.fetchScheduleMetadata()).thenReturn(metadata);
    when(client.fetchCapacity(any(), any(), any())).thenReturn(CapacityResponse.empty());
    when(detector.detect(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(store.diffAndStore(anyList(), any())).thenReturn(List.of());

    service.checkOnce();
    service.checkOnce();

    verify(client, times(1)).fetchScheduleMetadata();
    verify(client, times(2)).fetchCapacity(any(), any(), any());
  }

  @Test
  void notifyFailurePropagatesAsFailure() {
    ScheduleMetadata metadata = new ScheduleMetadata(1L, 2L, anyOpenTimes(), 1800, NOW);
    when(client.fetchScheduleMetadata()).thenReturn(metadata);
    when(client.fetchCapacity(any(), any(), any())).thenReturn(CapacityResponse.empty());
    AvailableSlot slot =
        new AvailableSlot(Instant.parse("2026-05-20T06:00:00Z"), Instant.parse("2026-05-20T06:30:00Z"));
    when(detector.detect(any(), any(), any(), any(), any())).thenReturn(List.of(slot));
    when(store.diffAndStore(anyList(), any())).thenReturn(List.of(slot));
    Mockito.doThrow(new RuntimeException("telegram down"))
        .when(notifier)
        .notifyAvailable(anyList(), anyString(), any());

    SlotMonitorService.CheckOutcome outcome = service.checkOnce();

    assertThat(outcome).isInstanceOf(SlotMonitorService.CheckOutcome.Failure.class);
    SlotMonitorService.CheckOutcome.Failure f = (SlotMonitorService.CheckOutcome.Failure) outcome;
    assertThat(f.phase()).isEqualTo(SlotMonitorService.CheckOutcome.Phase.NOTIFY);
  }

  private static List<Integer> anyOpenTimes() {
    return List.of(0, 540, 540, 540, 540, 540, 0, 1440, 840, 840, 840, 840, 840, 1440);
  }
}
