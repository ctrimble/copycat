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
package net.kuujo.copycat.coordination;

import net.kuujo.copycat.Resource;
import net.kuujo.copycat.coordination.state.LeaderElectionCommands;
import net.kuujo.copycat.coordination.state.LeaderElectionState;
import net.kuujo.copycat.raft.StateMachine;
import net.kuujo.copycat.resource.ResourceContext;
import net.kuujo.copycat.util.Listener;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Asynchronous leader election resource.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class DistributedLeaderElection extends Resource {
  private final Set<Consumer<Long>> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

  @Override
  protected Class<? extends StateMachine> stateMachine() {
    return LeaderElectionState.class;
  }

  @Override
  protected void open(ResourceContext context) {
    super.open(context);
    context.session().<Long>onEvent(epoch -> {
      for (Consumer<Long> listener : listeners) {
        listener.accept(epoch);
      }
    });
  }

  /**
   * Registers a listener to be called when this client is elected.
   *
   * @param listener The listener to register.
   * @return A completable future to be completed with the listener context.
   */
  public CompletableFuture<Listener<Long>> onElection(Consumer<Long> listener) {
    if (!listeners.isEmpty()) {
      listeners.add(listener);
      return CompletableFuture.completedFuture(new ElectionListener(listener));
    }

    listeners.add(listener);
    return submit(LeaderElectionCommands.Listen.builder().build())
      .thenApply(v -> new ElectionListener(listener));
  }

  /**
   * Verifies that the client is the current leader.
   *
   * @param epoch The epoch for which to check if this client is the leader.
   * @return A completable future to be completed with a boolean value indicating whether the
   *         client is the current leader.
   */
  public CompletableFuture<Boolean> isLeader(long epoch) {
    return submit(LeaderElectionCommands.IsLeader.builder().withEpoch(epoch).build());
  }

  /**
   * Change listener context.
   */
  private class ElectionListener implements Listener<Long> {
    private final Consumer<Long> listener;

    private ElectionListener(Consumer<Long> listener) {
      this.listener = listener;
    }

    @Override
    public void accept(Long epoch) {
      listener.accept(epoch);
    }

    @Override
    public void close() {
      synchronized (DistributedLeaderElection.this) {
        listeners.remove(listener);
        if (listeners.isEmpty()) {
          submit(LeaderElectionCommands.Unlisten.builder().build());
        }
      }
    }
  }

}
