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
package net.kuujo.copycat.state;

import net.jodah.concurrentunit.ConcurrentTestCase;
import net.kuujo.copycat.cluster.ClusterConfig;
import net.kuujo.copycat.log.BufferedLog;
import net.kuujo.copycat.protocol.LocalProtocol;
import net.kuujo.copycat.raft.Consistency;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;

/**
 * State log test.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
@Test
public class StateLogTest extends ConcurrentTestCase {

  /**
   * Tests querying with strong consistency.
   */
  @SuppressWarnings("unchecked")
  public void testQueryWithStrongConsistency() throws Throwable {
    LocalProtocol.reset();
    ClusterConfig cluster = new ClusterConfig()
      .withProtocol(new LocalProtocol())
      .addMember("foo", "local://foo")
      .addMember("bar", "local://bar")
      .addMember("baz", "local://baz");
    StateLog<String> log1 = StateLog.<String>create(new StateLogConfig("test").withLog(new BufferedLog()).withDefaultConsistency(Consistency.STRONG), cluster.copy().withLocalMember("local://foo")).registerQuery("test", v -> v);
    StateLog<String> log2 = StateLog.<String>create(new StateLogConfig("test").withLog(new BufferedLog()).withDefaultConsistency(Consistency.STRONG), cluster.copy().withLocalMember("local://bar")).registerQuery("test", v -> v);
    StateLog<String> log3 = StateLog.<String>create(new StateLogConfig("test").withLog(new BufferedLog()).withDefaultConsistency(Consistency.STRONG), cluster.copy().withLocalMember("local://baz")).registerQuery("test", v -> v);

    CompletableFuture<StateLog<String>>[] futures = new CompletableFuture[3];
    futures[0] = log1.open();
    futures[1] = log2.open();
    futures[2] = log3.open();

    expectResume();
    CompletableFuture.allOf(futures).thenRun(this::resume);
    await(15000);

    expectResume();
    log1.submit("test", "Hello world!").thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      resume();
    });
    await(5000);
  }

  /**
   * Tests snapshot replication.
   */
  @SuppressWarnings("unchecked")
  public void testSnapshotReplication() throws Throwable {
    LocalProtocol.reset();
    ClusterConfig cluster = new ClusterConfig()
      .withProtocol(new LocalProtocol())
      .addMember("foo", "local://foo")
      .addMember("bar", "local://bar")
      .addMember("baz", "local://baz");
    StateLog<String> log1 = StateLog.<String>create(new StateLogConfig("test")
      .withLog(new BufferedLog().withSegmentSize(1024))
      .withDefaultConsistency(Consistency.STRONG),
      cluster.copy().withLocalMember("local://foo"))
      .registerCommand("command", v -> v)
      .registerQuery("query", v -> v)
      .snapshotWith(() -> "Snapshot data")
      .installWith(s -> {
        threadAssertEquals(s, "Snapshot data");
        resume();
      });
    StateLog<String> log2 = StateLog.<String>create(new StateLogConfig("test")
        .withLog(new BufferedLog().withSegmentSize(1024))
        .withDefaultConsistency(Consistency.STRONG),
      cluster.copy().withLocalMember("local://bar"))
      .registerCommand("command", v -> v)
      .registerQuery("query", v -> v)
      .snapshotWith(() -> "Snapshot data")
      .installWith(s -> {
        threadAssertEquals(s, "Snapshot data");
        resume();
      });
    StateLog<String> log3 = StateLog.<String>create(new StateLogConfig("test")
        .withLog(new BufferedLog().withSegmentSize(1024))
        .withDefaultConsistency(Consistency.STRONG),
      cluster.copy().withLocalMember("local://baz"))
      .registerCommand("command", v -> v)
      .registerQuery("query", v -> v)
      .snapshotWith(() -> "Snapshot data")
      .installWith(s -> {
        threadAssertEquals(s, "Snapshot data");
        resume();
      });

    CompletableFuture<StateLog<String>>[] futures = new CompletableFuture[2];
    futures[0] = log1.open();
    futures[1] = log2.open();

    expectResume();
    CompletableFuture.allOf(futures).thenRun(this::resume);
    await(5000);

    // Append enough entries to force the log to roll over to a new segment.
    String entry = "Hello world!";
    expectResumes((int) Math.ceil(1025 / (double) entry.getBytes().length));
    for (int i = 0; i < 1025; i += entry.getBytes().length) {
      log1.submit("command", entry).thenRun(this::resume);
    }
    await(5000);

    // Once the log has been rolled over, start a new log and await the snapshot replication.
    // This should cause two resumes. One when the log is opened and one when the snapshot is installed.
    expectResumes(2);
    log3.open().thenRun(this::resume);
    await(5000);
  }

}