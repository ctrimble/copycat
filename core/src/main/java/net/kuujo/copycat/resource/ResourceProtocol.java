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
package net.kuujo.copycat.resource;

import net.kuujo.copycat.raft.Command;
import net.kuujo.copycat.raft.Protocol;
import net.kuujo.copycat.raft.Query;
import net.kuujo.copycat.raft.Raft;
import net.kuujo.copycat.resource.manager.DeleteResource;

import java.util.concurrent.CompletableFuture;

/**
 * Resource protocol.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class ResourceProtocol implements Protocol {
  private final long resource;
  private final Raft raft;

  public ResourceProtocol(long resource, Raft raft) {
    this.resource = resource;
    this.raft = raft;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<T> submit(Command<T> command) {
    return raft.submit(ResourceCommand.builder()
      .withResource(resource)
      .withCommand(command)
      .build());
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<T> submit(Query<T> query) {
    return raft.submit(ResourceQuery.builder()
      .withResource(resource)
      .withQuery(query)
      .build());
  }

  @Override
  public CompletableFuture<Void> delete() {
    return raft.submit(DeleteResource.builder()
      .withResource(resource)
      .build())
      .thenApply(deleted -> null);
  }

}