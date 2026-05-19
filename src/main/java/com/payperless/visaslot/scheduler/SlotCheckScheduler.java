package com.payperless.visaslot.scheduler;

import com.payperless.visaslot.monitor.SlotMonitorService;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class SlotCheckScheduler {

  private static final Logger log = LoggerFactory.getLogger(SlotCheckScheduler.class);

  private final SlotMonitorService monitor;

  public SlotCheckScheduler(SlotMonitorService monitor) {
    this.monitor = monitor;
  }

  @Scheduled(fixedDelayString = "${monitor.check-interval}", initialDelay = 5_000)
  public void tick() {
    Instant start = Instant.now();
    try {
      SlotMonitorService.CheckOutcome outcome = monitor.checkOnce();
      log.debug(
          "Check completed in {} ms with outcome {}",
          Duration.between(start, Instant.now()).toMillis(),
          outcome);
    } catch (RuntimeException unexpected) {
      log.error("Slot check threw unexpectedly; will run again next tick", unexpected);
    }
  }
}
