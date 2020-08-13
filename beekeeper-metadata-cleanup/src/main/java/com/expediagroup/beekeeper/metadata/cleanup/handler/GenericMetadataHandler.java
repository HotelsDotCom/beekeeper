/**
 * Copyright (C) 2019-2020 Expedia, Inc.
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
package com.expediagroup.beekeeper.metadata.cleanup.handler;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.expediagroup.beekeeper.cleanup.metadata.MetadataCleaner;
import com.expediagroup.beekeeper.cleanup.path.PathCleaner;
import com.expediagroup.beekeeper.core.model.HousekeepingMetadata;
import com.expediagroup.beekeeper.core.model.HousekeepingStatus;
import com.expediagroup.beekeeper.core.model.LifecycleEventType;
import com.expediagroup.beekeeper.core.repository.HousekeepingMetadataRepository;

public abstract class GenericMetadataHandler {

  private final Logger log = LoggerFactory.getLogger(GenericMetadataHandler.class);

  public abstract HousekeepingMetadataRepository getHousekeepingMetadataRepository();

  public abstract LifecycleEventType getLifecycleType();

  public abstract MetadataCleaner getMetadataCleaner();

  public abstract PathCleaner getPathCleaner();

  public abstract Page<HousekeepingMetadata> findRecordsToClean(LocalDateTime instant, Pageable pageable);

  public abstract Long countPartitionsForDatabaseAndTable(LocalDateTime instant, String databaseName, String tableName, boolean dryRunEnabled);

  /**
   * Processes a pageable HouseKeepingMetadata page.
   *
   * @param pageable Pageable to iterate through for dryRun
   * @param page Page to get content from
   * @param dryRunEnabled Dry Run boolean flag
   * @return Pageable to pass to query. In the case of dry runs, this is the next page.
   * @implNote This parent handler expects the child's cleanupMetadata call to update & remove the record from this call
   * such that subsequent DB queries will not return the record. Hence why we only call next during dryRuns
   * where no updates occur.
   * @implNote Note that we only expect pageable.next to be called during a dry run.
   */
  public Pageable processPage(Pageable pageable, LocalDateTime instant, Page<HousekeepingMetadata> page,
      boolean dryRunEnabled) {
    List<HousekeepingMetadata> pageContent = page.getContent();
    if (dryRunEnabled) {
      pageContent.forEach(metadata -> cleanupMetadata(metadata, instant, true));
      return pageable.next();
    } else {
      pageContent.forEach(metadata -> cleanupContent(metadata, instant, false));
      return pageable;
    }
  }

  private boolean cleanupMetadata(HousekeepingMetadata housekeepingMetadata, LocalDateTime instant, boolean dryRunEnabled) {
    MetadataCleaner metadataCleaner = getMetadataCleaner();
    PathCleaner pathCleaner = getPathCleaner();
    String partitionName = housekeepingMetadata.getPartitionName();
    if (partitionName != null) {
      cleanupPartition(housekeepingMetadata, metadataCleaner, pathCleaner);
      return true;
    } else {
      Long partitionCount = countPartitionsForDatabaseAndTable(instant, housekeepingMetadata.getDatabaseName(),
          housekeepingMetadata.getTableName(), dryRunEnabled);
      if (partitionName == null && partitionCount == Long.valueOf(0)) {
        cleanUpTable(housekeepingMetadata, metadataCleaner, pathCleaner);
        return true;
      }
    }
    return false;
  }

  private void cleanUpTable(
      HousekeepingMetadata housekeepingMetadata,
      MetadataCleaner metadataCleaner,
      PathCleaner pathCleaner) {
    String databaseName = housekeepingMetadata.getDatabaseName();
    String tableName = housekeepingMetadata.getTableName();
    if (metadataCleaner.tableExists(databaseName, tableName)) {
      metadataCleaner.dropTable(housekeepingMetadata);
      pathCleaner.cleanupPath(housekeepingMetadata);
    } else {
      log.info("Cannot drop table \"{}.{}\". Table does not exist.", databaseName, tableName);
    }
  }

  private void cleanupPartition(
      HousekeepingMetadata housekeepingMetadata,
      MetadataCleaner metadataCleaner,
      PathCleaner pathCleaner) {
    String databaseName = housekeepingMetadata.getDatabaseName();
    String tableName = housekeepingMetadata.getTableName();
    if (metadataCleaner.tableExists(databaseName, tableName)) {
      boolean partitionDeleted = metadataCleaner.dropPartition(housekeepingMetadata);
      if (partitionDeleted) {
        pathCleaner.cleanupPath(housekeepingMetadata);
      }
    } else {
      log.info(
          "Cannot drop partition \"{}\" from table \"{}.{}\". Table does not exist.",
          housekeepingMetadata.getPartitionName(), databaseName, tableName);
    }
  }

  private void cleanupContent(HousekeepingMetadata housekeepingMetadata, LocalDateTime instant, boolean dryRunEnabled) {
    try {
      log
          .info("Cleaning up metadata for table \"{}.{}\"", housekeepingMetadata.getDatabaseName(),
              housekeepingMetadata.getTableName());
      boolean deleted = cleanupMetadata(housekeepingMetadata, instant, dryRunEnabled);
      if (deleted) {
        updateAttemptsAndStatus(housekeepingMetadata, HousekeepingStatus.DELETED);
      }
    } catch (Exception e) {
      updateAttemptsAndStatus(housekeepingMetadata, HousekeepingStatus.FAILED);
      log
          .warn("Unexpected exception when deleting metadata for table \"{}.{}\"",
              housekeepingMetadata.getDatabaseName(),
              housekeepingMetadata.getTableName(), e);
    }
  }

  private void updateAttemptsAndStatus(HousekeepingMetadata housekeepingMetadata, HousekeepingStatus status) {
    housekeepingMetadata.setCleanupAttempts(housekeepingMetadata.getCleanupAttempts() + 1);
    housekeepingMetadata.setHousekeepingStatus(status);
    getHousekeepingMetadataRepository().save(housekeepingMetadata);
  }
}
