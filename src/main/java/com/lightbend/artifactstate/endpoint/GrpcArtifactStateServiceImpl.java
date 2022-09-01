package com.lightbend.artifactstate.endpoint;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.stream.javadsl.Source;
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor;
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor.ArtifactCommand;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GrpcArtifactStateServiceImpl implements ArtifactStateService {
    ActorSystem<Void> system;
    ActorRef<ShardingEnvelope<ArtifactCommand>> psCommandActor;
    Duration timeout;

    public GrpcArtifactStateServiceImpl(ActorSystem<Void> system, ActorRef<ShardingEnvelope<ArtifactCommand>> psCommandActor) {
        this.system = system;
        this.psCommandActor = psCommandActor;
        this.timeout = system.settings().config().getDuration("app.routes.ask-timeout");
    }

    @Override
    public CompletionStage<ArtifactStateProto.ExtResponse> isArtifactReadByUser(ArtifactStateProto.ArtifactAndUser in) {
        CompletionStage<ArtifactStateEntityActor.ArtifactReadByUser> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<ArtifactStateEntityActor.ArtifactReadByUser> replyTo) -> new ShardingEnvelope<>(String.format("%d%s", in.getArtifactId(), in.getUserId()), new ArtifactStateEntityActor.IsArtifactReadByUser(replyTo, in.getArtifactId(), in.getUserId())),
                        timeout,
                        system.scheduler());
        return result.thenApply(reply -> {
            if (reply != null)
                return ArtifactStateProto.ExtResponse.newBuilder()
                        .setArtifactId(in.getArtifactId())
                        .setUserId(in.getUserId())
                        .setAnswer(reply.artifactRead())
                        .build();
            else
                return ArtifactStateProto.ExtResponse.newBuilder()
                        .setArtifactId(in.getArtifactId())
                        .setUserId(in.getUserId())
                        .setAnswer(false)
                        .build();
        }).exceptionally(throwable -> ArtifactStateProto.ExtResponse.newBuilder()
                .setArtifactId(in.getArtifactId())
                .setUserId(in.getUserId())
                .setAnswer(false)
                .build());
    }

    @Override
    public CompletionStage<ArtifactStateProto.ExtResponse> isArtifactInUserFeed(ArtifactStateProto.ArtifactAndUser in) {
        CompletionStage<ArtifactStateEntityActor.ArtifactInUserFeed> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<ArtifactStateEntityActor.ArtifactInUserFeed> replyTo) ->
                                new ShardingEnvelope<>(String.format("%d%s", in.getArtifactId(), in.getUserId()), new ArtifactStateEntityActor.IsArtifactInUserFeed(replyTo, in.getArtifactId(), in.getUserId())),
                        timeout,
                        system.scheduler());
        return result.thenApply(reply -> {
            if (reply != null)
                return ArtifactStateProto.ExtResponse.newBuilder()
                        .setArtifactId(in.getArtifactId())
                        .setUserId(in.getUserId())
                        .setAnswer(reply.artifactInUserFeed())
                        .build();
            else
                return ArtifactStateProto.ExtResponse.newBuilder()
                        .setArtifactId(in.getArtifactId())
                        .setUserId(in.getUserId())
                        .setAnswer(false)
                        .build();
        }).exceptionally(throwable -> ArtifactStateProto.ExtResponse.newBuilder()
                .setArtifactId(in.getArtifactId())
                .setUserId(in.getUserId())
                .setAnswer(false)
                .build());
    }

    @Override
    public CompletionStage<ArtifactStateProto.AllStatesResponse> getAllStates(ArtifactStateProto.ArtifactAndUser in) {
        CompletionStage<ArtifactStateEntityActor.AllStates> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<ArtifactStateEntityActor.AllStates> replyTo) ->
                                new ShardingEnvelope<>(String.format("%d%s", in.getArtifactId(), in.getUserId()), new ArtifactStateEntityActor.GetAllStates(replyTo, in.getArtifactId(), in.getUserId())),
                        timeout,
                        system.scheduler());
        return result.thenApply(reply -> {
            if (reply != null)
                return ArtifactStateProto.AllStatesResponse.newBuilder()
                        .setArtifactId(in.getArtifactId())
                        .setUserId(in.getUserId())
                        .setArtifactRead(reply.artifactRead())
                        .setArtifactInUserFeed(reply.artifactInUserFeed())
                        .build();
            else
                return ArtifactStateProto.AllStatesResponse.newBuilder()
                        .setArtifactId(in.getArtifactId())
                        .setUserId(in.getUserId())
                        .setArtifactRead(false)
                        .setArtifactInUserFeed(false)
                        .build();
        }).exceptionally(throwable -> ArtifactStateProto.AllStatesResponse.newBuilder()
                .setArtifactId(in.getArtifactId())
                .setUserId(in.getUserId())
                .setArtifactRead(false)
                .setArtifactInUserFeed(false)
                .build());
    }

    private CompletionStage<ArtifactStateProto.CommandResponse> handleCommandResponse(CompletionStage<ArtifactStateEntityActor.Okay> result) {
        return result.thenApply(reply -> {
            if (reply != null)
                return ArtifactStateProto.CommandResponse.newBuilder()
                        .setSuccess(true)
                        .build();
            else
                return ArtifactStateProto.CommandResponse.newBuilder()
                        .setSuccess(false)
                        .build();
        }).exceptionally(throwable -> ArtifactStateProto.CommandResponse.newBuilder()
                .setSuccess(false)
                .build());
    }

    @Override
    public CompletionStage<ArtifactStateProto.CommandResponse> setArtifactReadByUser(ArtifactStateProto.ArtifactAndUser in) {
        CompletionStage<ArtifactStateEntityActor.Okay> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<ArtifactStateEntityActor.Okay> replyTo) ->
                                new ShardingEnvelope<>(String.format("%d%s", in.getArtifactId(), in.getUserId()), new ArtifactStateEntityActor.SetArtifactRead(replyTo, in.getArtifactId(), in.getUserId())),
                        timeout,
                        system.scheduler());
        return handleCommandResponse(result);
    }

    @Override
    public CompletionStage<ArtifactStateProto.CommandResponse> setArtifactAddedToUserFeed(ArtifactStateProto.ArtifactAndUser in) {
        CompletionStage<ArtifactStateEntityActor.Okay> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<ArtifactStateEntityActor.Okay> replyTo) ->
                                new ShardingEnvelope<>(String.format("%d%s", in.getArtifactId(), in.getUserId()), new ArtifactStateEntityActor.SetArtifactAddedToUserFeed(replyTo, in.getArtifactId(), in.getUserId())),
                        timeout,
                        system.scheduler());
        return handleCommandResponse(result);
    }

    @Override
    public CompletionStage<ArtifactStateProto.CommandResponse> setArtifactRemovedFromUserFeed(ArtifactStateProto.ArtifactAndUser in) {
        CompletionStage<ArtifactStateEntityActor.Okay> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<ArtifactStateEntityActor.Okay> replyTo) ->
                                new ShardingEnvelope<>(String.format("%d%s", in.getArtifactId(), in.getUserId()), new ArtifactStateEntityActor.SetArtifactRemovedFromUserFeed(replyTo, in.getArtifactId(), in.getUserId())),
                        timeout,
                        system.scheduler());
        return handleCommandResponse(result);
    }

    @Override
    public Source<ArtifactStateProto.StreamedResponse, NotUsed> commandsStreamed(Source<ArtifactStateProto.ArtifactCommand, NotUsed> in) {
        ArrayList<String> validCommands = new ArrayList<>();
        validCommands.add("SetArtifactReadByUser");
        validCommands.add("SetArtifactAddedToUserFeed");
        validCommands.add("SetArtifactRemovedFromUserFeed");

        return in.mapAsync(5, command -> {
            if (validCommands.contains(command.getCommand())) {
                CompletionStage<ArtifactStateEntityActor.Okay> result = null;
                if (command.getCommand().equals("SetArtifactReadByUser")) {
                    result = AskPattern.ask(
                            psCommandActor,
                            (ActorRef<ArtifactStateEntityActor.Okay> replyTo) ->
                                    new ShardingEnvelope<>(String.format("%d%s", command.getArtifactId(), command.getUserId()), new ArtifactStateEntityActor.SetArtifactRead(replyTo, command.getArtifactId(), command.getUserId())),
                            timeout,
                            system.scheduler()
                    );
                }
                else if (command.getCommand().equals("SetArtifactAddedToUserFeed")) {
                    result = AskPattern.ask(
                            psCommandActor,
                            (ActorRef<ArtifactStateEntityActor.Okay> replyTo) ->
                                    new ShardingEnvelope<>(String.format("%d%s", command.getArtifactId(), command.getUserId()), new ArtifactStateEntityActor.SetArtifactAddedToUserFeed(replyTo, command.getArtifactId(), command.getUserId())),
                            timeout,
                            system.scheduler()
                    );
                }
                else if (command.getCommand().equals("SetArtifactRemovedFromUserFeed")) {
                    result = AskPattern.ask(
                                psCommandActor,
                                (ActorRef<ArtifactStateEntityActor.Okay> replyTo) ->
                                        new ShardingEnvelope<>(String.format("%d%s", command.getArtifactId(), command.getUserId()), new ArtifactStateEntityActor.SetArtifactRemovedFromUserFeed(replyTo, command.getArtifactId(), command.getUserId())),
                                timeout,
                                system.scheduler()
                    );
                }
                return handleCommandResponse(result).thenApply(reply -> ArtifactStateProto.StreamedResponse.newBuilder()
                        .setSuccess(reply.getSuccess())
                        .setCommand(command)
                        .build());
            }
            else {
                String errMsg = String.format("invalid command received %s for user:%s artifact id:%d", command.getCommand(), command.getUserId(), command.getArtifactId());
                system.log().error(errMsg);
                return CompletableFuture.supplyAsync(() -> ArtifactStateProto.StreamedResponse.newBuilder()
                        .setFailureMsg(errMsg)
                        .setCommand(command)
                        .build());
            }
        });

    }
}
