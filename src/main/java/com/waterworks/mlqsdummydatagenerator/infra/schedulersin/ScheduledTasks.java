package com.waterworks.mlqsdummydatagenerator.infra.schedulersin;

import com.waterworks.mlqsdummydatagenerator.app.generators.GeneratorRandomEvents;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Component responsible for scheduled tasks.
 * <p>
 * This component handles scheduled tasks such as populating the projects cache at fixed intervals.
 * </p>
 *
 * <p>
 * Author: Edgar Thomson
 * Version: 1.0
 * </p>
 */
@Component
@AllArgsConstructor
@EnableScheduling
public class ScheduledTasks {

  final GeneratorRandomEvents generatorRandomEvents;

  @Scheduled(fixedRate = 100)
  public void generateRandomEvents() {
    generatorRandomEvents.generate();
  }
}
