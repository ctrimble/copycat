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

import net.kuujo.copycat.io.BufferInput;
import net.kuujo.copycat.io.BufferOutput;
import net.kuujo.copycat.io.serializer.CopycatSerializable;
import net.kuujo.copycat.io.serializer.Serializer;
import net.kuujo.copycat.util.Assert;

/**
 * Resource event.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class ResourceEvent<T> implements CopycatSerializable {
  private long resource;
  private T event;

  public ResourceEvent() {
  }

  /**
   * @throws NullPointerException if {@code message} is null
   */
  public ResourceEvent(long resource, T event) {
    this.resource = resource;
    this.event = Assert.notNull(event, "event");
  }

  /**
   * Returns the resource ID.
   *
   * @return The resource ID.
   */
  public long resource() {
    return resource;
  }

  /**
   * Returns the event body.
   *
   * @return The meeventssage body.
   */
  public T event() {
    return event;
  }

  @Override
  public void writeObject(BufferOutput buffer, Serializer serializer) {
    buffer.writeLong(resource);
    serializer.writeObject(event, buffer);
  }

  @Override
  public void readObject(BufferInput buffer, Serializer serializer) {
    resource = buffer.readLong();
    event = serializer.readObject(buffer);
  }

  @Override
  public String toString() {
    return String.format("%s[resource=%d, message=%s]", getClass().getSimpleName(), resource, event);
  }

}
