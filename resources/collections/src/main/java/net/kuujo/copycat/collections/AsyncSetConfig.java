/*
 * Copyright 2014 the original author or authors.
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

import net.kuujo.copycat.collections.internal.collection.SetState;
import net.kuujo.copycat.resource.ResourceConfig;
import net.kuujo.copycat.state.StateMachineConfig;

import java.util.Map;

/**
 * Asynchronous set configuration.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class AsyncSetConfig extends AsyncCollectionConfig<AsyncSetConfig> {
  private static final String DEFAULT_CONFIGURATION = "set-defaults";
  private static final String CONFIGURATION = "set";

  public AsyncSetConfig() {
    super(CONFIGURATION, DEFAULT_CONFIGURATION);
  }

  public AsyncSetConfig(Map<String, Object> config) {
    super(config, CONFIGURATION, DEFAULT_CONFIGURATION);
  }

  public AsyncSetConfig(String resource) {
    super(resource, CONFIGURATION, DEFAULT_CONFIGURATION);
    setDefaultName(resource);
  }

  protected AsyncSetConfig(AsyncSetConfig config) {
    super(config);
  }

  @Override
  public AsyncSetConfig copy() {
    return new AsyncSetConfig(this);
  }

  @Override
  public ResourceConfig<?> resolve() {
    return new StateMachineConfig(toMap())
      .withStateType(SetState.class)
      .withInitialState(SetState.class)
      .resolve();
  }

}