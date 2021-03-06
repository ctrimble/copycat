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
package net.kuujo.copycat.raft.storage;

import net.kuujo.copycat.io.storage.Entry;
import net.kuujo.copycat.raft.protocol.Operation;
import net.kuujo.copycat.util.ReferenceManager;

/**
 * Operation entry.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public abstract class OperationEntry<T extends OperationEntry<T>> extends SessionEntry<T> {

  protected OperationEntry() {
  }

  protected OperationEntry(ReferenceManager<Entry<?>> referenceManager) {
    super(referenceManager);
  }

  /**
   * Returns the entry operation.
   *
   * @return The entry operation.
   */
  public abstract Operation getOperation();

}
