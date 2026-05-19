package com.payperless.visaslot.supersaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payperless.visaslot.model.ScheduleMetadata;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ScheduleMetadataParserTest {

  private final ScheduleMetadataParser parser =
      new ScheduleMetadataParser(Clock.fixed(Instant.parse("2026-05-19T12:00:00Z"), ZoneOffset.UTC));

  @Test
  void parsesAllFieldsFromSamplePage() throws IOException {
    String html =
        new String(
            new ClassPathResource("sample-schedule-page.html").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);

    ScheduleMetadata metadata = parser.parse(html);

    assertThat(metadata.rpId()).isEqualTo(782348L);
    assertThat(metadata.token()).isEqualTo(1367387L);
    assertThat(metadata.slotLengthSeconds()).isEqualTo(1800);
    assertThat(metadata.openTimes())
        .containsExactly(0, 540, 540, 540, 540, 540, 0, 1440, 840, 840, 840, 840, 840, 1440);
    assertThat(metadata.fetchedAt()).isEqualTo(Instant.parse("2026-05-19T12:00:00Z"));
  }

  @Test
  void defaultsSlotLengthWhenMissing() {
    String html = "<script>var rp_id=1, token=2; var open_times=[" + repeat14(60) + "]</script>";
    ScheduleMetadata metadata = parser.parse(html);
    assertThat(metadata.slotLengthSeconds()).isEqualTo(1800);
  }

  @Test
  void throwsWhenRpIdAbsent() {
    String html = "<script>var token=2; var open_times=[" + repeat14(60) + "]</script>";
    assertThatThrownBy(() -> parser.parse(html))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("rp_id");
  }

  @Test
  void throwsWhenOpenTimesTooShort() {
    String html = "<script>var rp_id=1, token=2; var open_times=[1,2,3]</script>";
    assertThatThrownBy(() -> parser.parse(html))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("open_times");
  }

  private static String repeat14(int v) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 14; i++) {
      if (i > 0) sb.append(',');
      sb.append(v);
    }
    return sb.toString();
  }
}
