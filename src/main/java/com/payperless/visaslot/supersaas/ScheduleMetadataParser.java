package com.payperless.visaslot.supersaas;

import com.payperless.visaslot.model.ScheduleMetadata;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ScheduleMetadataParser {

  private static final Pattern RP_ID = Pattern.compile("rp_id\\s*=\\s*(\\d+)");
  private static final Pattern TOKEN = Pattern.compile("token\\s*=\\s*(\\d+)");
  private static final Pattern OPEN_TIMES =
      Pattern.compile("open_times\\s*=\\s*\\[([^\\]]+)\\]");
  private static final Pattern DEFAULT_LENGTH =
      Pattern.compile("default_length\\s*=\\s*(\\d+)");

  private final Clock clock;

  public ScheduleMetadataParser(Clock clock) {
    this.clock = clock;
  }

  public ScheduleMetadata parse(String html) {
    long rpId = requireLong(html, RP_ID, "rp_id");
    long token = requireLong(html, TOKEN, "token");
    List<Integer> openTimes = parseOpenTimes(html);
    int slotLength = (int) optionalLong(html, DEFAULT_LENGTH).orElse(1800L);
    return new ScheduleMetadata(rpId, token, openTimes, slotLength, clock.instant());
  }

  private static long requireLong(String html, Pattern p, String field) {
    return optionalLong(html, p)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Could not extract '" + field + "' from SuperSaaS schedule page"));
  }

  private static java.util.OptionalLong optionalLong(String html, Pattern p) {
    Matcher m = p.matcher(html);
    if (!m.find()) {
      return java.util.OptionalLong.empty();
    }
    return java.util.OptionalLong.of(Long.parseLong(m.group(1)));
  }

  private static List<Integer> parseOpenTimes(String html) {
    Matcher m = OPEN_TIMES.matcher(html);
    if (!m.find()) {
      throw new IllegalStateException("Could not find open_times in SuperSaaS schedule page");
    }
    String body = m.group(1);
    List<Integer> out = new ArrayList<>(14);
    for (String part : body.split(",")) {
      out.add(Integer.parseInt(part.trim()));
    }
    if (out.size() < 14) {
      throw new IllegalStateException(
          "open_times has unexpected shape; expected 14 entries, got " + out.size());
    }
    return List.copyOf(out);
  }
}
