package com.payperless.visaslot;

import com.payperless.visaslot.config.MonitorProperties;
import com.payperless.visaslot.config.SuperSaasProperties;
import com.payperless.visaslot.config.TelegramProperties;
import com.payperless.visaslot.supersaas.SlotDetector;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
@EnableConfigurationProperties({
  MonitorProperties.class,
  SuperSaasProperties.class,
  TelegramProperties.class
})
public class SlotCheckerApplication {

  public static void main(String[] args) {
    SpringApplication.run(SlotCheckerApplication.class, args);
  }

  @Bean
  public Clock systemClock() {
    return Clock.systemUTC();
  }

  @Bean
  public SlotDetector slotDetector() {
    return new SlotDetector();
  }
}
