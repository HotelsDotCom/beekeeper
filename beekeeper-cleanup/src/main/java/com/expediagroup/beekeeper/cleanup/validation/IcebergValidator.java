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
package com.expediagroup.beekeeper.cleanup.validation;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.expediagroup.beekeeper.cleanup.metadata.CleanerClient;
import com.expediagroup.beekeeper.core.error.BeekeeperIcebergException;

public class IcebergValidator {

  private static final Logger log = LoggerFactory.getLogger(IcebergValidator.class);

  private final CleanerClient client;

  public IcebergValidator(CleanerClient client) {
    this.client = client;
  }

  /**
   * Beekeeper does not support Iceberg format right now. Iceberg tables in Hive Metastore do not store partition information,
   * so Beekeeper tries to clean up the entire table because that information is missing. This method checks if
   * the table is an Iceberg table and throws BeekeeperIcebergException to stop the process.
   *
   * @param databaseName
   * @param tableName
   */
  public void throwExceptionIfIceberg(String databaseName, String tableName) {
    Map<String, String> parameters = client.getTableProperties(databaseName, tableName);
    String tableType = parameters.getOrDefault("table_type", "").toLowerCase();
    String format = parameters.getOrDefault("format", "").toLowerCase();
    String outputFormat = client.getOutputFormat(databaseName, tableName).toLowerCase();

    if (tableType.contains("iceberg") || format.contains("iceberg") || outputFormat.contains("iceberg")) {
      String errorMessage = String.format("Iceberg tables are not currently supported in Beekeeper. Detected in Database: '%s', Table: '%s'.", databaseName, tableName);
      throw new BeekeeperIcebergException(errorMessage);
    }
  }
}
