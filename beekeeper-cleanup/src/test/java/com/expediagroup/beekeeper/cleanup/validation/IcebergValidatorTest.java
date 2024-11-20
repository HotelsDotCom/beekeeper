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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.expediagroup.beekeeper.cleanup.metadata.CleanerClient;
import com.expediagroup.beekeeper.core.error.BeekeeperIcebergException;

public class IcebergValidatorTest {

  private CleanerClient cleanerClient;
  private IcebergValidator icebergValidator;

  @Before
  public void setUp() {
    cleanerClient = mock(CleanerClient.class);
    icebergValidator = new IcebergValidator(cleanerClient);
  }

  @Test(expected = BeekeeperIcebergException.class)
  public void shouldThrowExceptionWhenTableTypeIsIceberg() {
    Map<String, String> properties = new HashMap<>();
    properties.put("table_type", "ICEBERG");

    when(cleanerClient.getTableProperties("db", "table")).thenReturn(properties);
    when(cleanerClient.getOutputFormat("db", "table")).thenReturn("");

    icebergValidator.throwExceptionIfIceberg("db", "table");
  }

  @Test(expected = BeekeeperIcebergException.class)
  public void shouldThrowExceptionWhenFormatIsIceberg() {
    Map<String, String> properties = new HashMap<>();
    properties.put("format", "iceberg");

    when(cleanerClient.getTableProperties("db", "table")).thenReturn(properties);
    when(cleanerClient.getOutputFormat("db", "table")).thenReturn("");

    icebergValidator.throwExceptionIfIceberg("db", "table");
  }

  @Test
  public void shouldNotThrowExceptionForNonIcebergTable() {
    Map<String, String> properties = new HashMap<>();
    properties.put("table_type", "HIVE_TABLE");

    when(cleanerClient.getTableProperties("db", "table")).thenReturn(properties);
    when(cleanerClient.getOutputFormat("db", "table"))
        .thenReturn("org.apache.hadoop.hive.ql.io.orc.OrcOutputFormat");

    icebergValidator.throwExceptionIfIceberg("db", "table");
  }

  @Test(expected = BeekeeperIcebergException.class)
  public void shouldThrowExceptionWhenOutputFormatContainsIceberg() {
    Map<String, String> properties = new HashMap<>();

    when(cleanerClient.getTableProperties("db", "table")).thenReturn(properties);
    when(cleanerClient.getOutputFormat("db", "table"))
        .thenReturn("org.apache.iceberg.mr.hive.HiveIcebergOutputFormat");

    icebergValidator.throwExceptionIfIceberg("db", "table");
  }
}
