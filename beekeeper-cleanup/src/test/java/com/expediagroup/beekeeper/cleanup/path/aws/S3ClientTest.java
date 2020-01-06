/**
 * Copyright (C) 2019 Expedia, Inc.
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
package com.expediagroup.beekeeper.cleanup.path.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.localstack.LocalStackContainer;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;

class S3ClientTest {

  private static LocalStackContainer s3Container;

  private final String content = "content";
  private final String keyRoot = "table/partition_1";
  private final String key1 = "table/partition_1/file1";
  private final String key2 = "table/partition_1/file2";
  private final String bucket = "bucket";

  private S3Client s3Client;
  private S3Client s3ClientDryRun;
  private AmazonS3 amazonS3;

  @BeforeAll
  public static void s3() {
    s3Container = new LocalStackContainer().withServices(LocalStackContainer.Service.S3);
    s3Container.start();
  }

  @BeforeEach
  void setUp() {
    amazonS3 = AmazonS3Factory.newInstance(s3Container);
    amazonS3.createBucket(bucket);
    amazonS3.listObjectsV2(bucket)
      .getObjectSummaries()
      .forEach(object -> amazonS3.deleteObject(bucket, object.getKey()));
    s3Client = new S3Client(amazonS3, false);
    s3ClientDryRun = new S3Client(amazonS3, true);
  }

  @AfterAll
  static void teardown() {
    s3Container.stop();
  }

  @Test
  void deleteObject() {
    amazonS3.putObject(bucket, key1, content);
    s3Client.deleteObject(bucket, key1);
    assertThat(amazonS3.doesObjectExist(bucket, key1)).isFalse();
  }

  @Test
  void deleteObjectWithSpace() {
    String spacedKey = keyRoot + "/ /file";
    amazonS3.putObject(bucket, spacedKey, content);
    s3Client.deleteObject(bucket, spacedKey);
    assertThat(amazonS3.doesObjectExist(bucket, spacedKey)).isFalse();
  }

  @Test
  void deleteObjectsWithSpace() {
    String spacedKey1 = keyRoot + "/ /file1";
    String spacedKey2 = keyRoot + "/ /file2";
    amazonS3.putObject(bucket, spacedKey1, content);
    amazonS3.putObject(bucket, spacedKey2, content);
    s3Client.deleteObjects(bucket, List.of(spacedKey1, spacedKey2));
    assertThat(amazonS3.doesObjectExist(bucket, spacedKey1)).isFalse();
    assertThat(amazonS3.doesObjectExist(bucket, spacedKey2)).isFalse();
  }

  @Test
  void deleteObjectDryRun() {
    amazonS3.putObject(bucket, key1, content);
    s3ClientDryRun.deleteObject(bucket, key1);
    assertThat(amazonS3.doesObjectExist(bucket, key1)).isTrue();
  }

  @Test
  void listObjects() {
    amazonS3.putObject(bucket, key1, content);
    amazonS3.putObject(bucket, key2, content);

    List<S3ObjectSummary> result = s3Client.listObjects(bucket, keyRoot).getObjectSummaries();

    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0).getBucketName()).isEqualTo(bucket);
    assertThat(result.get(0).getKey()).isEqualTo(key1);
    assertThat(result.get(1).getBucketName()).isEqualTo(bucket);
    assertThat(result.get(1).getKey()).isEqualTo(key2);
  }

  @Test
  void listObjectsWithSpace() {
    String spacedKey1 = keyRoot + "/ /file1";
    String spacedKey2 = keyRoot + "/ /file2";
    amazonS3.putObject(bucket, spacedKey1, content);
    amazonS3.putObject(bucket, spacedKey2, content);

    List<S3ObjectSummary> result = s3Client.listObjects(bucket, keyRoot).getObjectSummaries();

    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0).getBucketName()).isEqualTo(bucket);
    assertThat(result.get(0).getKey()).isEqualTo(spacedKey1);
    assertThat(result.get(1).getBucketName()).isEqualTo(bucket);
    assertThat(result.get(1).getKey()).isEqualTo(spacedKey2);
  }

  @Test
  void listObjectsWithSpaceInSearch() {
    String spacedKeyRoot = keyRoot + "/ /";
    String spacedKey1 = spacedKeyRoot + "file1";
    String spacedKey2 = spacedKeyRoot + "file2";
    amazonS3.putObject(bucket, spacedKey1, content);
    amazonS3.putObject(bucket, spacedKey2, content);

    List<S3ObjectSummary> result = s3Client.listObjects(bucket, spacedKeyRoot).getObjectSummaries();

    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0).getBucketName()).isEqualTo(bucket);
    assertThat(result.get(0).getKey()).isEqualTo(spacedKey1);
    assertThat(result.get(1).getBucketName()).isEqualTo(bucket);
    assertThat(result.get(1).getKey()).isEqualTo(spacedKey2);
  }

  @Test
  void deleteObjectsInDirectory() {
    amazonS3.putObject(bucket, key1, content);
    amazonS3.putObject(bucket, key2, content);

    List<String> result = s3Client.deleteObjects(bucket, List.of(key1, key2));

    assertThat(result.size()).isEqualTo(2);
    assertThat(result).contains(key1);
    assertThat(result).contains(key2);
    assertThat(amazonS3.doesObjectExist(bucket, key1)).isFalse();
    assertThat(amazonS3.doesObjectExist(bucket, key2)).isFalse();
  }

  @Test
  void deleteObjectsInDirectoryDryRun() {
    amazonS3.putObject(bucket, key1, content);
    amazonS3.putObject(bucket, key2, content);

    List<String> result = s3ClientDryRun.deleteObjects(bucket, List.of(key1, key2));

    assertThat(result.size()).isEqualTo(2);
    assertThat(result).contains(key1);
    assertThat(result).contains(key2);
    assertThat(amazonS3.doesObjectExist(bucket, key1)).isTrue();
    assertThat(amazonS3.doesObjectExist(bucket, key2)).isTrue();
  }

  @Test
  void deleteObjectsEmptyRequest() {
    AmazonS3 amazonS3 = Mockito.mock(AmazonS3.class);
    S3Client s3Client = new S3Client(amazonS3, false);

    List<String> result = s3Client.deleteObjects(bucket, Collections.emptyList());

    assertThat(result.size()).isEqualTo(0);
    verifyNoMoreInteractions(amazonS3);
  }

  @Test
  void doesObjectExistForFile() {
    amazonS3.putObject(bucket, key2, content);
    boolean result = s3Client.doesObjectExist(bucket, key2);
    assertThat(result).isTrue();
  }

  @Test
  void doesObjectExistForFileWithSpaceInKey() {
    String spacedKey = key2 + "/ /file";
    amazonS3.putObject(bucket, spacedKey, content);
    boolean result = s3Client.doesObjectExist(bucket, spacedKey);
    assertThat(result).isTrue();
  }

  @Test
  void doesObjectExistForDirectory() {
    amazonS3.putObject(bucket, key2, content);
    boolean result = s3Client.doesObjectExist(bucket, keyRoot);
    assertThat(result).isFalse();
  }

  @Test
  void doesObjectExistForDirectoryWithTrailingSlash() {
    amazonS3.putObject(bucket, key2, content);
    boolean result = s3Client.doesObjectExist(bucket, keyRoot + "/");
    assertThat(result).isFalse();
  }

  @Test
  void getObjectSize() {
    amazonS3.putObject(bucket, key2, content);
    ObjectMetadata result = s3Client.getObjectMetadata(bucket, key2);
    assertThat(result.getContentLength()).isNotEqualTo(0L);
  }

  @Test
  void getObjectSizeWithSpaceInKey() {
    String spacedKey = key2 + "/ /file";
    amazonS3.putObject(bucket, spacedKey, content);
    ObjectMetadata result = s3Client.getObjectMetadata(bucket, spacedKey);
    assertThat(result.getContentLength()).isNotEqualTo(0L);
  }

  @Test
  void getObjectSizeForEmptyFile() {
    amazonS3.putObject(bucket, key2, "");
    ObjectMetadata result = s3Client.getObjectMetadata(bucket, key2);
    assertThat(result.getContentLength()).isEqualTo(0L);
  }

  @Test
  void isEmptyForNonEmptyDirectory() {
    amazonS3.putObject(bucket, key1, content);
    boolean result = s3Client.isEmpty(bucket, keyRoot, null);
    assertThat(result).isFalse();
  }

  @Test
  void isEmptyForNonEmptyDirectoryWithSpacedKey() {
    String spacedKey = key1 + "/ /file";
    amazonS3.putObject(bucket, spacedKey, content);
    boolean result = s3Client.isEmpty(bucket, keyRoot, null);
    assertThat(result).isFalse();
  }

  @Test
  void isEmptyForEmptyDirectory() {
    boolean result = s3Client.isEmpty(bucket, keyRoot, null);
    assertThat(result).isTrue();
  }

  @Test
  void isEmptyDryRunForNonEmptyDirectoryAndCorrectLeafKey() {
    amazonS3.putObject(bucket, key1, content);
    boolean result = s3ClientDryRun.isEmpty(bucket, "table", keyRoot);
    assertThat(result).isTrue();
  }

  @Test
  void isEmptyDryRunForEmptyDirectory() {
    boolean result = s3ClientDryRun.isEmpty(bucket, "table", keyRoot);
    assertThat(result).isTrue();
  }

  @Test
  void isEmptyDryRunForOtherHighLevelDirectory() {
    amazonS3.putObject(bucket, "table/partition_2", content);
    boolean result = s3ClientDryRun.isEmpty(bucket, "table", keyRoot);
    assertThat(result).isFalse();
  }

  @Test
  void doesObjectExist() {
    amazonS3.putObject(bucket, key1, content);
    boolean result = s3Client.doesObjectExist(bucket, key1);
    assertThat(result).isTrue();
  }

  @Test
  void doesObjectExistWithSpacedKey() {
    String spacedKey = key1 + "/ /file";
    amazonS3.putObject(bucket, spacedKey, content);
    boolean result = s3Client.doesObjectExist(bucket, spacedKey);
    assertThat(result).isTrue();
  }

  @Test
  void isEmptyDryRunForCommonFolderName() {
    String otherPartition = "table/test/test/partition10";
    String folder1 = "table";
    String folder2 = "table/test";
    String folder3 = "table/test/test";
    String folder4 = "table/test/test/partition1";

    String filePath = "table/test/test/partition1/test";
    String sentinel1 = "table_$folder$";
    String sentinel2 = "table/test_$folder$";
    String sentinel3 = "table/test/test_$folder$";
    String sentinel4 = "table/test/test/partition1_$folder$";
    amazonS3.putObject(bucket, filePath, content);
    amazonS3.putObject(bucket, sentinel1, "");
    amazonS3.putObject(bucket, sentinel2, "");
    amazonS3.putObject(bucket, sentinel3, "");
    amazonS3.putObject(bucket, sentinel4, "");

    assertThat(s3ClientDryRun.isEmpty(bucket, folder3, folder4)).isTrue();
    assertThat(s3ClientDryRun.isEmpty(bucket, folder3, otherPartition)).isFalse();
    assertThat(s3ClientDryRun.isEmpty(bucket, folder2, folder3)).isTrue();
    assertThat(s3ClientDryRun.isEmpty(bucket, folder1, folder2)).isTrue();
  }
}
