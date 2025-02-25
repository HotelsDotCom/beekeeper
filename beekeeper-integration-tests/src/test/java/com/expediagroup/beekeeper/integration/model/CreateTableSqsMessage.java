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
package com.expediagroup.beekeeper.integration.model;

import static com.expedia.apiary.extensions.receiver.common.event.EventType.CREATE_TABLE;

import java.io.IOException;
import java.net.URISyntaxException;

public class CreateTableSqsMessage extends SqsMessage {

  public CreateTableSqsMessage(
      String tableLocation,
      boolean isExpired
  ) throws IOException, URISyntaxException {
    super(CREATE_TABLE);
    setTableLocation(tableLocation);
    setExpired(isExpired);
  }

  public CreateTableSqsMessage(
      String tableLocation,
      boolean isIceberg,
      boolean isExpired
  ) throws IOException, URISyntaxException {
    super(CREATE_TABLE);
    setTableLocation(tableLocation);
    setExpired(isExpired);
    if (isIceberg) {
      setIceberg();
    }
  }
}
