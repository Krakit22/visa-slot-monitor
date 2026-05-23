package com.payperless.visaslot.telegram;

import com.payperless.visaslot.config.MonitorProperties;
import com.payperless.visaslot.config.TelegramProperties;
import com.payperless.visaslot.model.AvailableSlot;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Renders the notification body and hands it to {@link TelegramClient}. Honours the {@code
 * telegram.enabled} flag — when disabled, messages are logged instead so the rest of the pipeline
 * still exercises end-to-end.
 */
@Component
public class TelegramNotifier {

  private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
  private static final int MAX_SLOTS_IN_MESSAGE = 12;
  private static final DateTimeFormatter SLOT_FMT =
      DateTimeFormatter.ofPattern("EEE d MMM HH:mm");
  private static final DateTimeFormatter CHECK_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

  private final TelegramClient client;
  private final TelegramProperties telegramProperties;
  private final MonitorProperties monitorProperties;

  public TelegramNotifier(
      TelegramClient client,
      TelegramProperties telegramProperties,
      MonitorProperties monitorProperties) {
    this.client = client;
    this.telegramProperties = telegramProperties;
    this.monitorProperties = monitorProperties;
  }

  public void notifyAvailable(List<AvailableSlot> slots, String bookingUrl, Instant checkedAt) {
    if (slots.isEmpty()) {
      return;
    }
    String text = renderMessage(slots, bookingUrl, checkedAt);
    if (!telegramProperties.enabled()) {
      log.info("Telegram disabled — would have sent:\n{}", text);
      return;
    }
    client.sendMessage(text);
    log.info("Telegram notification delivered for {} slot(s)", slots.size());
  }

  String renderMessage(List<AvailableSlot> slots, String bookingUrl, Instant checkedAt) {
    ZoneId zone = monitorProperties.scheduleZone();
    BookingLinkBuilder linkBuilder = new BookingLinkBuilder(bookingUrl);
    StringBuilder sb = new StringBuilder();
    sb.append("<b>VISA appointment slot update</b>\n");
    sb.append(slots.size()).append(" new slot(s) since last check:\n\n");

    int shown = Math.min(slots.size(), MAX_SLOTS_IN_MESSAGE);
    for (int i = 0; i < shown; i++) {
      AvailableSlot slot = slots.get(i);
      String label = SLOT_FMT.format(slot.from().atZone(zone));
      String href = linkBuilder.buildSlotUrl(slot, checkedAt);
      sb.append("• <a href=\"")
          .append(escape(href))
          .append("\">")
          .append(label)
          .append("</a> ")
          .append(statusTag(slot))
          .append('\n');
    }
    if (slots.size() > shown) {
      sb.append("… and ").append(slots.size() - shown).append(" more\n");
    }
    sb.append("\n<a href=\"")
        .append(escape(bookingUrl))
        .append("\">Open full schedule</a>\n");
    sb.append("Checked at ").append(CHECK_FMT.format(checkedAt.atZone(zone)));
    return sb.toString();
  }

  private static String statusTag(AvailableSlot slot) {
    if (slot.capacity() <= 0) {
      return "";
    }
    if (slot.isFree()) {
      return "(" + slot.freeSpots() + " free)";
    }
    return "(" + slot.capacity() + "/" + slot.capacity() + " booked)";
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}
