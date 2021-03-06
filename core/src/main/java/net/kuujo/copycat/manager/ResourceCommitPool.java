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
package net.kuujo.copycat.manager;

import net.kuujo.copycat.raft.session.Session;
import net.kuujo.copycat.raft.Commit;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Resource commit pool.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
class ResourceCommitPool {
  private final Queue<ResourceCommit> pool = new ConcurrentLinkedQueue<>();

  /**
   * Acquires a commit from the pool.
   *
   * @param commit The commit to acquire.
   * @param session The resource session.
   * @return The acquired resource commit.
   */
  @SuppressWarnings("unchecked")
  public ResourceCommit acquire(Commit commit, Session session) {
    ResourceCommit resourceCommit = pool.poll();
    if (resourceCommit == null) {
      resourceCommit = new ResourceCommit(this);
    }
    resourceCommit.reset(commit, session);
    return resourceCommit;
  }

  /**
   * Releases a commit to the pool.
   *
   * @param commit The commit to release.
   */
  public void release(ResourceCommit commit) {
    pool.add(commit);
  }

}
