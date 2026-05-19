package com.payperless.visaslot.telegram;

import com.payperless.visaslot.model.AvailableSlot;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Builds a SuperSaaS deep-link that opens the day-view directly on a particular slot.
 *
 * <p>The format mirrors the JS helper {@code date_to_url} from {@code capacity.js}: all date
 * components are derived from the UTC representation of the timestamp, the {@code year} segment
 * is emitted only when it differs from the current UTC year, and {@code min} is dropped when 0.
 * The page itself converts those UTC components back to local time for display, so a slot whose
 * Telegram label reads "Wed 20 May 09:00" (Asia/Nicosia) round-trips through {@code hour=6} in the
 * URL — which is what the browser flow produces.
 */
public final class BookingLinkBuilder {

  private final String baseBookingUrl;

  public BookingLinkBuilder(String baseBookingUrl) {
    this.baseBookingUrl = baseBookingUrl;
  }

  public String buildSlotUrl(AvailableSlot slot, Instant referenceInstant) {
    OffsetDateTime utc = slot.from().atOffset(ZoneOffset.UTC);
    int currentYear = referenceInstant.atOffset(ZoneOffset.UTC).getYear();

    StringBuilder url = new StringBuilder(baseBookingUrl);
    url.append(baseBookingUrl.contains("?") ? '&' : '?');
    url.append("view=day");
    url.append("&day=").append(utc.getDayOfMonth());
    url.append("&month=").append(utc.getMonthValue());
    if (utc.getYear() != currentYear) {
      url.append("&year=").append(utc.getYear());
    }
    url.append("&hour=").append(utc.getHour());
    if (utc.getMinute() != 0) {
      url.append("&min=").append(utc.getMinute());
    }
    return url.toString();
  }
}
