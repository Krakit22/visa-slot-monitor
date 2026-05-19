package com.payperless.visaslot.telegram;

import com.payperless.visaslot.config.TelegramProperties;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Thin wrapper around Telegram Bot API's {@code sendMessage}. */
@Component
public class TelegramClient {

  private final RestClient restClient;
  private final TelegramProperties properties;

  public TelegramClient(RestClient telegramRestClient, TelegramProperties properties) {
    this.restClient = telegramRestClient;
    this.properties = properties;
  }

  @Retryable(
      retryFor = {RestClientResponseException.class, java.io.IOException.class},
      maxAttempts = 4,
      backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000))
  public void sendMessage(String text) {
    if (properties.botToken() == null || properties.botToken().isBlank()) {
      throw new IllegalStateException("telegram.bot-token is not configured");
    }
    if (properties.chatId() == null || properties.chatId().isBlank()) {
      throw new IllegalStateException("telegram.chat-id is not configured");
    }
    Map<String, Object> body =
        Map.of(
            "chat_id", properties.chatId(),
            "text", text,
            "parse_mode", "HTML",
            "disable_web_page_preview", false);
    restClient
        .post()
        .uri("/bot{token}/sendMessage", properties.botToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }
}
