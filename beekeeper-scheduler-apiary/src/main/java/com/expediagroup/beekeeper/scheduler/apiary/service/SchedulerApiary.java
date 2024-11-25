/**
 * Copyright (C) 2019-2024 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expediagroup.beekeeper.scheduler.apiary.service;

import static java.lang.String.format;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.expediagroup.beekeeper.cleanup.validation.IcebergValidator;
import com.expediagroup.beekeeper.core.error.BeekeeperException;
import com.expediagroup.beekeeper.core.error.BeekeeperIcebergException;
import com.expediagroup.beekeeper.core.model.HousekeepingEntity;
import com.expediagroup.beekeeper.core.model.LifecycleEventType;
import com.expediagroup.beekeeper.scheduler.apiary.messaging.BeekeeperEventReader;
import com.expediagroup.beekeeper.scheduler.apiary.model.BeekeeperEvent;
import com.expediagroup.beekeeper.scheduler.service.SchedulerService;

@Component
public class SchedulerApiary {

  private static final Logger log = LoggerFactory.getLogger(SchedulerApiary.class);

  private final BeekeeperEventReader beekeeperEventReader;
  private final EnumMap<LifecycleEventType, SchedulerService> schedulerServiceMap;
  private final IcebergValidator icebergValidator;

  @Autowired
  public SchedulerApiary(
      BeekeeperEventReader beekeeperEventReader,
      EnumMap<LifecycleEventType, SchedulerService> schedulerServiceMap,
      IcebergValidator icebergValidator
  ) {
    this.beekeeperEventReader = beekeeperEventReader;
    this.schedulerServiceMap = schedulerServiceMap;
    this.icebergValidator = icebergValidator;
  }

  @Transactional
  public void scheduleBeekeeperEvent() {
    Optional<BeekeeperEvent> housekeepingEntitiesToBeScheduled = beekeeperEventReader.read();
    if (housekeepingEntitiesToBeScheduled.isEmpty()) {return;}
    BeekeeperEvent beekeeperEvent = housekeepingEntitiesToBeScheduled.get();
    List<HousekeepingEntity> housekeepingEntities = beekeeperEvent.getHousekeepingEntities();

    for (HousekeepingEntity entity : housekeepingEntities) {
      try {
        icebergValidator.throwExceptionIfIceberg(entity.getDatabaseName(), entity.getTableName());
        LifecycleEventType eventType = LifecycleEventType.valueOf(entity.getLifecycleType());
        SchedulerService scheduler = schedulerServiceMap.get(eventType);
        scheduler.scheduleForHousekeeping(entity);
      } catch (BeekeeperIcebergException e) {
        log.warn("Iceberg table are not supported in Beekeeper. Deleting message from queue", e);
      } catch (Exception e) {
        throw new BeekeeperException(format(
            "Unable to schedule %s deletion for entity, this message will go back on the queue",
            entity.getLifecycleType()), e);
      }
    }
    beekeeperEventReader.delete(beekeeperEvent);
  }

  public void close() throws IOException {
    beekeeperEventReader.close();
  }
}
