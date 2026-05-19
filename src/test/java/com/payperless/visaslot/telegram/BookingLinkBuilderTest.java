package com.payperless.visaslot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.payperless.visaslot.model.AvailableSlot;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingLinkBuilderTest {

  private static final String BASE =
      "https://www.supersaas.com/schedule/EmbassyofGreeceinCyprus/VISA";
  private static final Instant REF = Instant.parse("2026-05-19T08:00:00Z");

  private final BookingLinkBuilder builder = new BookingLinkBuilder(BASE);

  @Test
  void buildsCurrentYearLinkWithoutYearParam() {
    AvailableSlot s =
        new AvailableSlot(Instant.parse("2026-05-20T06:00:00Z"), Instant.parse("2026-05-20T06:30:00Z"));
    assertThat(builder.buildSlotUrl(s, REF))
        .isEqualTo(BASE + "?view=day&day=20&month=5&hour=6");
  }

  @Test
  void emitsMinWhenNonZero() {
    AvailableSlot s =
        new AvailableSlot(Instant.parse("2026-05-21T07:30:00Z"), Instant.parse("2026-05-21T08:00:00Z"));
    assertThat(builder.buildSlotUrl(s, REF))
        .isEqualTo(BASE + "?view=day&day=21&month=5&hour=7&min=30");
  }

  @Test
  void emitsYearWhenItDiffersFromReference() {
    AvailableSlot s =
        new AvailableSlot(Instant.parse("2027-01-15T08:00:00Z"), Instant.parse("2027-01-15T08:30:00Z"));
    assertThat(builder.buildSlotUrl(s, REF))
        .isEqualTo(BASE + "?view=day&day=15&month=1&year=2027&hour=8");
  }

  @Test
  void appendsParamsToBaseUrlThatAlreadyHasQuery() {
    BookingLinkBuilder b = new BookingLinkBuilder(BASE + "?lang=en");
    AvailableSlot s =
        new AvailableSlot(Instant.parse("2026-05-20T06:00:00Z"), Instant.parse("2026-05-20T06:30:00Z"));
    assertThat(b.buildSlotUrl(s, REF))
        .isEqualTo(BASE + "?lang=en&view=day&day=20&month=5&hour=6");
  }

  @Test
  void midnightSlotOmitsMin() {
    AvailableSlot s =
        new AvailableSlot(Instant.parse("2026-05-20T00:00:00Z"), Instant.parse("2026-05-20T00:30:00Z"));
    assertThat(builder.buildSlotUrl(s, REF))
        .isEqualTo(BASE + "?view=day&day=20&month=5&hour=0");
  }
}
