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
package net.kuujo.copycat.raft.state;

import net.kuujo.copycat.cluster.Member;
import net.kuujo.copycat.cluster.TypedMemberInfo;
import net.kuujo.copycat.raft.RaftError;
import net.kuujo.copycat.raft.rpc.*;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Remote state.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class RemoteState extends AbstractState {
  private final AtomicBoolean keepAlive = new AtomicBoolean();
  private final Random random = new Random();
  private ScheduledFuture<?> currentTimer;

  public RemoteState(RaftContext context) {
    super(context);
  }

  @Override
  public RaftState type() {
    return RaftState.REMOTE;
  }

  @Override
  public synchronized CompletableFuture<AbstractState> open() {
    return super.open().thenCompose(v -> register()).thenRun(this::startKeepAliveTimer).thenApply(v -> this);
  }

  /**
   * Registers the client.
   */
  private CompletableFuture<Long> register() {
    return register(context.getCluster()
      .members()
      .stream()
      .filter(m -> m.type() == Member.Type.ACTIVE)
      .collect(Collectors.toList()), new CompletableFuture<>());
  }

  /**
   * Registers the client by contacting a random member.
   */
  private CompletableFuture<Long> register(List<Member> members, CompletableFuture<Long> future) {
    if (members.isEmpty()) {
      future.completeExceptionally(RaftError.Type.NO_LEADER_ERROR.createException());
      return future;
    }

    Member member;
    if (context.getLeader() != 0) {
      member = context.getCluster().member(context.getLeader());
    } else {
      member = members.remove(random.nextInt(members.size()));
    }

    RegisterRequest request = RegisterRequest.builder()
      .withMember(context.getCluster().member().info())
      .build();
    member.<RegisterRequest, RegisterResponse>send(context.getTopic(), request).whenComplete((response, error) -> {
      if (error == null) {
        context.setTerm(response.term());
        context.setLeader(response.leader());
        context.getCluster().configure(response.members().toArray(new TypedMemberInfo[response.members().size()])).whenComplete((configureResult, configureError) -> {
          if (configureError == null) {
            future.complete(response.session());
          } else {
            future.completeExceptionally(configureError);
          }
        });
      } else {
        register(members, future);
      }
    });
    return future;
  }

  /**
   * Starts the keep alive timer.
   */
  private void startKeepAliveTimer() {
    LOGGER.debug("{} - Setting keep alive timer", context.getCluster().member().id());
    currentTimer = context.getContext().scheduleAtFixedRate(this::keepAlive, 1, context.getHeartbeatInterval(), TimeUnit.MILLISECONDS);
  }

  /**
   * Sends a keep alive request to a random member.
   */
  private void keepAlive() {
    if (keepAlive.compareAndSet(false, true)) {
      keepAlive(context.getCluster()
        .members()
        .stream()
        .filter(m -> m.type() == Member.Type.ACTIVE)
        .collect(Collectors.toList()), new CompletableFuture<>());
    }
  }

  /**
   * Registers the client by contacting a random member.
   */
  private CompletableFuture<Void> keepAlive(List<Member> members, CompletableFuture<Void> future) {
    if (members.isEmpty()) {
      future.completeExceptionally(RaftError.Type.NO_LEADER_ERROR.createException());
      keepAlive.set(false);
      return future;
    }

    Member member;
    if (context.getLeader() != 0) {
      member = context.getCluster().member(context.getLeader());
    } else {
      member = members.remove(random.nextInt(members.size()));
    }

    KeepAliveRequest request = KeepAliveRequest.builder()
      .withSession(context.getCluster().member().session().id())
      .build();
    member.<KeepAliveRequest, KeepAliveResponse>send(context.getTopic(), request).whenComplete((response, error) -> {
      if (error == null) {
        context.setTerm(response.term());
        context.setLeader(response.leader());
        context.getCluster().configure(response.members().toArray(new TypedMemberInfo[response.members().size()])).whenComplete((configureResult, configureError) -> {
          if (configureError == null) {
            future.complete(null);
          } else {
            future.completeExceptionally(configureError);
          }
          keepAlive.set(false);
        });
      } else {
        keepAlive(members, future);
      }
    });
    return future;
  }

  @Override
  protected CompletableFuture<SyncResponse> sync(SyncRequest request) {
    context.checkThread();
    logRequest(request);
    return CompletableFuture.completedFuture(logResponse(SyncResponse.builder()
      .withStatus(Response.Status.ERROR)
      .withError(RaftError.Type.ILLEGAL_MEMBER_STATE_ERROR)
      .build()));
  }

  @Override
  protected CompletableFuture<AppendResponse> append(AppendRequest request) {
    context.checkThread();
    logRequest(request);
    return CompletableFuture.completedFuture(logResponse(AppendResponse.builder()
      .withStatus(Response.Status.ERROR)
      .withError(RaftError.Type.ILLEGAL_MEMBER_STATE_ERROR)
      .build()));
  }

  @Override
  protected CompletableFuture<PollResponse> poll(PollRequest request) {
    context.checkThread();
    logRequest(request);
    return CompletableFuture.completedFuture(logResponse(PollResponse.builder()
      .withStatus(Response.Status.ERROR)
      .withError(RaftError.Type.ILLEGAL_MEMBER_STATE_ERROR)
      .build()));
  }

  @Override
  protected CompletableFuture<VoteResponse> vote(VoteRequest request) {
    context.checkThread();
    logRequest(request);
    return CompletableFuture.completedFuture(logResponse(VoteResponse.builder()
      .withStatus(Response.Status.ERROR)
      .withError(RaftError.Type.ILLEGAL_MEMBER_STATE_ERROR)
      .build()));
  }

  @Override
  public CompletableFuture<SubmitResponse> submit(SubmitRequest request) {
    context.checkThread();
    logRequest(request);
    if (context.getLeader() == 0) {
      return CompletableFuture.completedFuture(logResponse(SubmitResponse.builder()
        .withStatus(Response.Status.ERROR)
        .withError(RaftError.Type.NO_LEADER_ERROR)
        .build()));
    } else {
      return context.getCluster().member(context.getLeader()).send(context.getTopic(), request);
    }
  }

  @Override
  protected CompletableFuture<KeepAliveResponse> keepAlive(KeepAliveRequest request) {
    context.checkThread();
    logRequest(request);
    if (context.getLeader() == 0) {
      return CompletableFuture.completedFuture(logResponse(KeepAliveResponse.builder()
        .withStatus(Response.Status.ERROR)
        .withError(RaftError.Type.NO_LEADER_ERROR)
        .build()));
    } else {
      return context.getCluster().member(context.getLeader()).send(context.getTopic(), request);
    }
  }

  @Override
  protected CompletableFuture<RegisterResponse> register(RegisterRequest request) {
    context.checkThread();
    logRequest(request);
    if (context.getLeader() == 0) {
      return CompletableFuture.completedFuture(logResponse(RegisterResponse.builder()
        .withStatus(Response.Status.ERROR)
        .withError(RaftError.Type.NO_LEADER_ERROR)
        .build()));
    } else {
      return context.getCluster().member(context.getLeader()).send(context.getTopic(), request);
    }
  }

  @Override
  protected CompletableFuture<LeaveResponse> leave(LeaveRequest request) {
    context.checkThread();
    logRequest(request);
    if (context.getLeader() == 0) {
      return CompletableFuture.completedFuture(logResponse(LeaveResponse.builder()
        .withStatus(Response.Status.ERROR)
        .withError(RaftError.Type.NO_LEADER_ERROR)
        .build()));
    } else {
      return context.getCluster().member(context.getLeader()).send(context.getTopic(), request);
    }
  }

  @Override
  protected CompletableFuture<JoinResponse> join(JoinRequest request) {
    context.checkThread();
    logRequest(request);
    if (context.getLeader() == 0) {
      return CompletableFuture.completedFuture(logResponse(JoinResponse.builder()
        .withStatus(Response.Status.ERROR)
        .withError(RaftError.Type.NO_LEADER_ERROR)
        .build()));
    } else {
      return context.getCluster().member(context.getLeader()).send(context.getTopic(), request);
    }
  }

  @Override
  protected CompletableFuture<PromoteResponse> promote(PromoteRequest request) {
    context.checkThread();
    logRequest(request);
    if (context.getLeader() == 0) {
      return CompletableFuture.completedFuture(logResponse(PromoteResponse.builder()
        .withStatus(Response.Status.ERROR)
        .withError(RaftError.Type.NO_LEADER_ERROR)
        .build()));
    } else {
      return context.getCluster().member(context.getLeader()).send(context.getTopic(), request);
    }
  }

  @Override
  protected CompletableFuture<DemoteResponse> demote(DemoteRequest request) {
    context.checkThread();
    logRequest(request);
    if (context.getLeader() == 0) {
      return CompletableFuture.completedFuture(logResponse(DemoteResponse.builder()
        .withStatus(Response.Status.ERROR)
        .withError(RaftError.Type.NO_LEADER_ERROR)
        .build()));
    } else {
      return context.getCluster().member(context.getLeader()).send(context.getTopic(), request);
    }
  }

  /**
   * Cancels the status timer.
   */
  private void cancelStatusTimer() {
    if (currentTimer != null) {
      LOGGER.debug("{} - Cancelling status timer", context.getCluster().member().id());
      currentTimer.cancel(false);
    }
  }

  @Override
  public synchronized CompletableFuture<Void> close() {
    return super.close().thenRun(this::cancelStatusTimer);
  }

}