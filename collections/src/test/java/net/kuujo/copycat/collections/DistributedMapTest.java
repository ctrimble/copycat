/*
 * Copyright 2015 the original author or authors.
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
package net.kuujo.copycat.collections;

import net.jodah.concurrentunit.ConcurrentTestCase;
import net.kuujo.copycat.Copycat;
import net.kuujo.copycat.CopycatReplica;
import net.kuujo.copycat.io.storage.Storage;
import net.kuujo.copycat.io.transport.Address;
import net.kuujo.copycat.io.transport.LocalServerRegistry;
import net.kuujo.copycat.io.transport.LocalTransport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Distributed map test.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
@Test
public class DistributedMapTest extends ConcurrentTestCase {
  private static final File directory = new File("test-logs");

  /**
   * Tests putting and getting a value.
   */
  @SuppressWarnings("unchecked")
  public void testPutGetRemove() throws Throwable {
    List<Copycat> copycats = createCopycats(3);

    Copycat copycat = copycats.get(0);

    DistributedMap<String, String> map = copycat.create("test", DistributedMap.class).get();

    map.put("foo", "Hello world!").thenRun(this::resume);
    await();

    map.get("foo").thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      resume();
    });
    await();

    map.remove("foo").thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      resume();
    });
    await();

    map.get("foo").thenAccept(result -> {
      threadAssertNull(result);
      resume();
    });
    await();

    copycats.forEach(c -> c.close().join());
  }

  /**
   * Tests the map count.
   */
  @SuppressWarnings("unchecked")
  public void testMapSize() throws Throwable {
    List<Copycat> copycats = createCopycats(3);

    Copycat copycat = copycats.get(0);

    DistributedMap<String, String> map = copycat.create("test", DistributedMap.class).get();

    map.size().thenAccept(size -> {
      threadAssertEquals(size, 0);
      resume();
    });
    await();

    map.put("foo", "Hello world!").thenRun(this::resume);
    await();

    map.size().thenAccept(size -> {
      threadAssertEquals(size, 1);
      resume();
    });
    await();

    map.put("bar", "Hello world again!").thenRun(this::resume);
    await();

    map.size().thenAccept(size -> {
      threadAssertEquals(size, 2);
      resume();
    });
    await();

    copycats.forEach(c -> c.close().join());
  }

  /**
   * Tests TTL.
   */
  @SuppressWarnings("unchecked")
  public void testMapTtl() throws Throwable {
    List<Copycat> copycats = createCopycats(3);

    Copycat copycat = copycats.get(0);

    DistributedMap<String, String> map = copycat.create("test", DistributedMap.class).get();

    map.put("foo", "Hello world!", Duration.ofSeconds(1)).thenRun(this::resume);
    await();

    map.get("foo").thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      resume();
    });
    await();

    Thread.sleep(3000);

    map.get("foo").thenAccept(result -> {
      threadAssertNull(result);
      resume();
    });
    await();

    map.size().thenAccept(size -> {
      threadAssertEquals(size, 0);
      resume();
    });
    await();
  }

  /**
   * Creates a Copycat instance.
   */
  private List<Copycat> createCopycats(int nodes) throws Throwable {
    LocalServerRegistry registry = new LocalServerRegistry();

    List<Copycat> copycats = new ArrayList<>();

    Collection<Address> members = new ArrayList<>();
    for (int i = 1; i <= nodes; i++) {
      members.add(new Address("localhost", 5000 + i));
    }

    for (int i = 1; i <= nodes; i++) {
      Copycat copycat = CopycatReplica.builder(new Address("localhost", 5000 + i), members)
        .withTransport(new LocalTransport(registry))
        .withStorage(Storage.builder()
          .withDirectory(new File(directory, "" + i))
          .build())
        .build();

      copycat.open().thenRun(this::resume);

      copycats.add(copycat);
    }

    await(0, nodes);

    return copycats;
  }

  @BeforeMethod
  @AfterMethod
  public void clearTests() throws IOException {
    deleteDirectory(directory);
  }

  /**
   * Deletes a directory recursively.
   */
  private void deleteDirectory(File directory) throws IOException {
    if (directory.exists()) {
      File[] files = directory.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            deleteDirectory(file);
          } else {
            Files.delete(file.toPath());
          }
        }
      }
      Files.delete(directory.toPath());
    }
  }

}
