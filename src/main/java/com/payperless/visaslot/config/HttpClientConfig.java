package com.payperless.visaslot.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

  @Bean
  public RestClient superSaasRestClient(SuperSaasProperties props) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build());
    factory.setReadTimeout(props.readTimeout());
    return RestClient.builder()
        .baseUrl(props.baseUrl())
        .defaultHeader("User-Agent", props.userAgent())
        .defaultHeader("Accept-Language", "en-US,en;q=0.9")
        .requestFactory(factory)
        .build();
  }

  @Bean
  public RestClient telegramRestClient(TelegramProperties props) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build());
    factory.setReadTimeout(props.readTimeout());
    return RestClient.builder()
        .baseUrl(props.apiBaseUrl())
        .requestFactory(factory)
        .build();
  }
}
