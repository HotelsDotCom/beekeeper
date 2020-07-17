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
package com.expediagroup.beekeeper.core.hive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.UnknownDBException;
import org.apache.thrift.TException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import com.expediagroup.beekeeper.core.error.BeekeeperException;

import com.hotels.hcommon.hive.metastore.client.api.CloseableMetaStoreClient;

public class HiveClientTest {

  private @Mock CloseableMetaStoreClient client = Mockito.mock(CloseableMetaStoreClient.class);

  private static final String DATABASE = "database";
  private static final String TABLE_NAME = "tableName";
  private static final String PARTITION_NAME = "event_date=2020-01-01/event_hour=0/event_type=A";

  private HiveClient hiveClient;

  @BeforeEach
  public void init() {
    hiveClient = new HiveClient(client, false);
  }

  @Test
  public void typicalMetadataDelete() throws MetaException, NoSuchObjectException, TException {
    boolean result = hiveClient.deleteMetadata(DATABASE, TABLE_NAME);
    verify(client).dropTable(DATABASE, TABLE_NAME);
    assertThat(result == true);
  }

  @Test
  public void typicalPartitionDrop() throws NoSuchObjectException, MetaException, TException {
    when(client.tableExists(DATABASE, TABLE_NAME)).thenReturn(true);
    boolean result = hiveClient.dropPartition(DATABASE, TABLE_NAME, PARTITION_NAME);
    verify(client).dropPartition(DATABASE, TABLE_NAME, PARTITION_NAME, false);
    assertThat(result == true);
  }

  @Test
  public void typicalMetadataDryRun() throws MetaException, NoSuchObjectException, TException {
    hiveClient = new HiveClient(client, true);
    boolean result = hiveClient.deleteMetadata(DATABASE, TABLE_NAME);
    verify(client, never()).dropTable(DATABASE, TABLE_NAME);
    assertThat(result == true);
  }

  @Test
  public void typicalPartitionDryRun() throws NoSuchObjectException, MetaException, TException {
    hiveClient = new HiveClient(client, true);
    boolean result = hiveClient.dropPartition(DATABASE, TABLE_NAME, PARTITION_NAME);
    verify(client, never()).dropPartition(DATABASE, TABLE_NAME, PARTITION_NAME, false);
    assertThat(result == true);
  }

  @Test
  public void onlyDropPartitionIfTableExists() throws MetaException, UnknownDBException, TException {
    when(client.tableExists(DATABASE, TABLE_NAME)).thenReturn(false);
    boolean result = hiveClient.dropPartition(DATABASE, TABLE_NAME, PARTITION_NAME);
    verify(client, never()).dropPartition(DATABASE, TABLE_NAME, PARTITION_NAME, false);
    assertThat(result == false);
  }

  @Test
  public void dontThrowErrorWhenTableAlreadyDeleted() throws MetaException, NoSuchObjectException, TException {
    when(client.tableExists(DATABASE, TABLE_NAME)).thenReturn(true);
    Mockito.doThrow(NoSuchObjectException.class).when(client).dropTable(DATABASE, TABLE_NAME);
    boolean result = hiveClient.deleteMetadata(DATABASE, TABLE_NAME);
    verify(client).dropTable(DATABASE, TABLE_NAME);
    assertThat(result == false);
  }

  @Test
  public void dontThrowErrorWhenPartitionAlreadyDeleted() throws MetaException, NoSuchObjectException, TException {
    when(client.tableExists(DATABASE, TABLE_NAME)).thenReturn(true);
    Mockito
        .doThrow(NoSuchObjectException.class)
        .when(client)
        .dropPartition(DATABASE, TABLE_NAME, PARTITION_NAME, false);

    boolean result = hiveClient.dropPartition(DATABASE, TABLE_NAME, PARTITION_NAME);
    verify(client).dropPartition(DATABASE, TABLE_NAME, PARTITION_NAME, false);
    assertThat(result == false);
  }

  @Test
  public void throwsExceptionForTableDelete() throws MetaException, NoSuchObjectException, TException {
    Mockito.doThrow(MetaException.class).when(client).dropTable(DATABASE, TABLE_NAME);
    Assertions.assertThrows(BeekeeperException.class, () -> {
      hiveClient.deleteMetadata(DATABASE, TABLE_NAME);
    });
  }

  @Test
  public void throwsExceptionForPartitionDelete() throws MetaException, NoSuchObjectException, TException {
    when(client.tableExists(DATABASE, TABLE_NAME)).thenReturn(true);
    Mockito.doThrow(MetaException.class).when(client).dropPartition(DATABASE, TABLE_NAME, PARTITION_NAME, false);
    Assertions.assertThrows(BeekeeperException.class, () -> {
      hiveClient.dropPartition(DATABASE, TABLE_NAME, PARTITION_NAME);
    });
  }

}
