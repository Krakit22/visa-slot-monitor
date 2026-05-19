package com.payperless.visaslot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.payperless.visaslot.config.MonitorProperties;
import com.payperless.visaslot.config.TelegramProperties;
import com.payperless.visaslot.model.AvailableSlot;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TelegramNotifierTest {

  private static final MonitorProperties monitorProps =
      new MonitorProperties(
          Duration.ofMinutes(5), Duration.ofDays(30), ZoneId.of("Asia/Nicosia"), Duration.ofHours(24));

  @Test
  void messageContainsCountSlotsAndBookingLink() {
    TelegramClient client = Mockito.mock(TelegramClient.class);
    TelegramNotifier notifier =
        new TelegramNotifier(client, enabledProps(), monitorProps);

    AvailableSlot s1 =
        new AvailableSlot(Instant.parse("2026-05-20T06:00:00Z"), Instant.parse("2026-05-20T06:30:00Z"));
    AvailableSlot s2 =
        new AvailableSlot(Instant.parse("2026-05-21T07:00:00Z"), Instant.parse("2026-05-21T07:30:00Z"));

    String msg =
        notifier.renderMessage(
            List.of(s1, s2),
            "https://www.supersaas.com/schedule/EmbassyofGreeceinCyprus/VISA",
            Instant.parse("2026-05-19T10:00:00Z"));

    assertThat(msg)
        .contains("Available VISA appointment slot found")
        .contains("2 new slot(s)")
        .contains("Open full schedule")
        .contains("https://www.supersaas.com/schedule/EmbassyofGreeceinCyprus/VISA");
  }

  @Test
  void everySlotGetsItsOwnDeepLink() {
    TelegramClient client = Mockito.mock(TelegramClient.class);
    TelegramNotifier notifier = new TelegramNotifier(client, enabledProps(), monitorProps);

    AvailableSlot s1 =
        new AvailableSlot(Instant.parse("2026-05-20T06:00:00Z"), Instant.parse("2026-05-20T06:30:00Z"));
    AvailableSlot s2 =
        new AvailableSlot(Instant.parse("2026-05-21T07:30:00Z"), Instant.parse("2026-05-21T08:00:00Z"));

    String msg =
        notifier.renderMessage(
            List.of(s1, s2),
            "https://www.supersaas.com/schedule/EmbassyofGreeceinCyprus/VISA",
            Instant.parse("2026-05-19T10:00:00Z"));

    // Wed 20 May 09:00 Nicosia = 06:00 UTC -> hour=6, no min, no year (2026 = current).
    assertThat(msg)
        .contains(
            "href=\"https://www.supersaas.com/schedule/EmbassyofGreeceinCyprus/VISA?view=day&amp;day=20&amp;month=5&amp;hour=6\"");
    // Thu 21 May 10:30 Nicosia = 07:30 UTC -> hour=7, min=30.
    assertThat(msg)
        .contains(
            "href=\"https://www.supersaas.com/schedule/EmbassyofGreeceinCyprus/VISA?view=day&amp;day=21&amp;month=5&amp;hour=7&amp;min=30\"");
  }

  @Test
  void doesNotCallTelegramWhenDisabled() {
    TelegramClient client = Mockito.mock(TelegramClient.class);
    TelegramNotifier notifier = new TelegramNotifier(client, disabledProps(), monitorProps);

    AvailableSlot s =
        new AvailableSlot(Instant.parse("2026-05-20T06:00:00Z"), Instant.parse("2026-05-20T06:30:00Z"));
    notifier.notifyAvailable(List.of(s), "https://example.com", Instant.parse("2026-05-19T10:00:00Z"));

    verify(client, never()).sendMessage(anyString());
  }

  @Test
  void sendsMessageWhenEnabled() {
    TelegramClient client = Mockito.mock(TelegramClient.class);
    TelegramNotifier notifier = new TelegramNotifier(client, enabledProps(), monitorProps);

    AvailableSlot s =
        new AvailableSlot(Instant.parse("2026-05-20T06:00:00Z"), Instant.parse("2026-05-20T06:30:00Z"));
    notifier.notifyAvailable(List.of(s), "https://example.com", Instant.parse("2026-05-19T10:00:00Z"));

    verify(client).sendMessage(anyString());
  }

  @Test
  void truncatesLongSlotLists() {
    TelegramClient client = Mockito.mock(TelegramClient.class);
    TelegramNotifier notifier = new TelegramNotifier(client, enabledProps(), monitorProps);

    Instant base = Instant.parse("2026-05-20T06:00:00Z");
    List<AvailableSlot> many =
        java.util.stream.IntStream.range(0, 20)
            .mapToObj(
                i ->
                    new AvailableSlot(
                        base.plusSeconds(i * 1800L), base.plusSeconds(i * 1800L + 1800)))
            .toList();

    String msg = notifier.renderMessage(many, "https://example.com", Instant.now());

    assertThat(msg).contains("and 8 more");
  }

  private static TelegramProperties enabledProps() {
    return new TelegramProperties(
        true,
        "test-token",
        "12345",
        "https://api.telegram.org",
        Duration.ofSeconds(5),
        Duration.ofSeconds(10));
  }

  private static TelegramProperties disabledProps() {
    return new TelegramProperties(
        false,
        "test-token",
        "12345",
        "https://api.telegram.org",
        Duration.ofSeconds(5),
        Duration.ofSeconds(10));
  }
}
