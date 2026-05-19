package com.payperless.visaslot.supersaas;

import com.fasterxml.jackson.databind.JsonNode;
import com.payperless.visaslot.config.SuperSaasProperties;
import com.payperless.visaslot.model.Appointment;
import com.payperless.visaslot.model.CapacityResponse;
import com.payperless.visaslot.model.ScheduleMetadata;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SuperSaasClient {

  /** SuperSaaS serialises {@code afrom/ato} in this exact tiny format. */
  private static final DateTimeFormatter SUPERSAAS_DT =
      DateTimeFormatter.ofPattern("yyyy-M-d HH:mm");

  private final RestClient restClient;
  private final SuperSaasProperties properties;
  private final ScheduleMetadataParser metadataParser;
  private final Clock clock;

  public SuperSaasClient(
      RestClient superSaasRestClient,
      SuperSaasProperties properties,
      ScheduleMetadataParser metadataParser,
      Clock clock) {
    this.restClient = superSaasRestClient;
    this.properties = properties;
    this.metadataParser = metadataParser;
    this.clock = clock;
  }

  @Retryable(
      retryFor = {RestClientResponseException.class, java.io.IOException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  public ScheduleMetadata fetchScheduleMetadata() {
    // Force the week view: the default ("list" / "free") page does not embed
    // open_times. With ?view=week the inline JS carries every constant we need.
    String path = withParam(properties.schedulePath(), "view", "week");
    String html =
        restClient
            .get()
            .uri(path)
            .header("Accept", "text/html,application/xhtml+xml")
            .retrieve()
            .body(String.class);
    if (html == null || html.isBlank()) {
      throw new IllegalStateException("Empty SuperSaaS schedule page response");
    }
    return metadataParser.parse(html);
  }

  private static String withParam(String path, String name, String value) {
    if (path.contains(name + "=")) {
      return path;
    }
    return path + (path.contains("?") ? "&" : "?") + name + "=" + value;
  }

  @Retryable(
      retryFor = {RestClientResponseException.class, java.io.IOException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000))
  public CapacityResponse fetchCapacity(ScheduleMetadata metadata, Instant from, Instant to) {
    String afrom = SUPERSAAS_DT.format(LocalDateTime.ofInstant(from, ZoneOffset.UTC));
    String ato = SUPERSAAS_DT.format(LocalDateTime.ofInstant(to, ZoneOffset.UTC));

    String uri =
        UriComponentsBuilder.fromPath("/ajax/capacity/{rpId}")
            .queryParam("token", metadata.token())
            .queryParam("afrom", afrom)
            .queryParam("ato", ato)
            .queryParam("efrom", afrom)
            .queryParam("eto", ato)
            .queryParam("ed", "r")
            .buildAndExpand(metadata.rpId())
            .toUriString();

    JsonNode body =
        restClient
            .get()
            .uri(uri)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Referer", properties.baseUrl() + properties.schedulePath())
            .retrieve()
            .body(JsonNode.class);

    if (body == null) {
      return CapacityResponse.empty();
    }
    return new CapacityResponse(parseEntries(body.get("app")), parseEntries(body.get("exc")));
  }

  private static List<Appointment> parseEntries(JsonNode array) {
    if (array == null || !array.isArray()) {
      return List.of();
    }
    List<Appointment> out = new ArrayList<>(array.size());
    for (JsonNode entry : array) {
      if (!entry.isArray() || entry.size() < 2) {
        continue;
      }
      long fromSec = entry.get(0).asLong();
      long toSec = entry.get(1).asLong();
      if (toSec <= fromSec) {
        continue;
      }
      long slotId = entry.size() > 2 ? entry.get(2).asLong(0L) : 0L;
      int capacity = entry.size() > 3 ? entry.get(3).asInt(0) : 0;
      int booked = entry.size() > 4 ? entry.get(4).asInt(0) : 0;
      out.add(
          new Appointment(
              Instant.ofEpochSecond(fromSec),
              Instant.ofEpochSecond(toSec),
              slotId,
              capacity,
              booked));
    }
    return out;
  }
}
