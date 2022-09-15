package com.lightbend.artifactstate.endpoint;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.StringUnmarshallers;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

import com.lightbend.artifactstate.actors.ArtifactStateEntityActor.*;
import com.lightbend.artifactstate.endpoint.ArtifactStatePocAPI.*;

public class ArtifactStateRoutes {

    ActorSystem<Void> system;
    ActorRef<ShardingEnvelope<ArtifactCommand>> psCommandActor;

    Duration timeout;

    public ArtifactStateRoutes(ActorSystem<Void> system, ActorRef<ShardingEnvelope<ArtifactCommand>> psCommandActor) {
        this.system = system;
        this.psCommandActor = psCommandActor;
        this.timeout = system.settings().config().getDuration("app.routes.ask-timeout");
    }

    private CompletionStage<ExtResponse> queryArtifactRead(Long artifactId, String userId) {
        CompletionStage<ArtifactReadByUser> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<ArtifactReadByUser> replyTo) -> new ShardingEnvelope<>(String.format("%d%s", artifactId, userId), new IsArtifactReadByUser(replyTo, artifactId, userId)),
                        timeout,
                        system.scheduler());
        return result.thenApply(reply -> {
            if (reply != null) {
                return new ExtResponse(artifactId, userId, reply.artifactRead());
            }
            else {
                return new ExtResponse(artifactId, userId, String.format("Artifact query not found for artifactId %d, userId %s", artifactId, userId));
            }
        }).exceptionally(throwable -> new ExtResponse(artifactId, userId, String.format("Artifact query failed for artifactId %d, userId %s : %s", artifactId, userId, throwable.getMessage())));
    }

    private CompletionStage<ExtResponse> queryArtifactInUserFeed(Long artifactId, String userId) {
        CompletionStage<ArtifactInUserFeed> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<ArtifactInUserFeed> replyTo) ->
                                new ShardingEnvelope<>(String.format("%d%s", artifactId, userId), new IsArtifactInUserFeed(replyTo, artifactId, userId)),
                        timeout,
                        system.scheduler());
        return result.thenApply(reply -> {
            if (reply != null)
                return new ExtResponse(artifactId, userId, reply.artifactInUserFeed());
            else
                return new ExtResponse(artifactId, userId, String.format("Artifact query not found for artifactId %d, userId %s", artifactId, userId));
        }).exceptionally(throwable -> new ExtResponse(artifactId, userId, String.format("Artifact query failed for artifactId %d, userId %s : %s", artifactId, userId, throwable.getMessage())));
    }

    private CompletionStage<AllStatesResponse> queryAllStates(Long artifactId, String userId) {
        CompletionStage<AllStates> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<AllStates> replyTo) ->
                                new ShardingEnvelope<>(String.format("%d%s", artifactId, userId), new GetAllStates(replyTo, artifactId, userId)),
                        timeout,
                        system.scheduler());
        return result.thenApply(reply -> {
            if (reply != null)
                return new AllStatesResponse(artifactId, userId, reply.artifactRead(), reply.artifactInUserFeed());
            else {
                return new AllStatesResponse(artifactId, userId, String.format("Artifact query not found for artifactId %d, userId %s", artifactId, userId));
            }
        }).exceptionally(throwable -> new AllStatesResponse(artifactId, userId, String.format("Artifact query failed for artifactId %d, userId %s : %s", artifactId, userId, throwable.getMessage())));
    }

    private CompletionStage<CommandResponse> handleCommandResponse(CompletionStage<Okay> result) {
        return result.thenApply(reply -> {
            if (reply != null)
                return new CommandResponse(true);
            else {
                return new CommandResponse(false);
            }
        }).exceptionally(throwable -> new CommandResponse(false));
    }

    private CompletionStage<CommandResponse> cmdArtifactRead(Long artifactId, String userId) {
        CompletionStage<Okay> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<Okay> replyTo) ->
                                new ShardingEnvelope<>(String.format("%d%s", artifactId, userId), new SetArtifactRead(replyTo, artifactId, userId)),
                        timeout,
                        system.scheduler());
        return handleCommandResponse(result);
    }

    private CompletionStage<CommandResponse> cmdArtifactAddedToUserFeed(Long artifactId, String userId) {
        CompletionStage<Okay> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<Okay> replyTo) ->
                                new ShardingEnvelope<>(String.format("%d%s", artifactId, userId), new SetArtifactAddedToUserFeed(replyTo, artifactId, userId)),
                        timeout,
                        system.scheduler());
        return handleCommandResponse(result);
    }

    private CompletionStage<CommandResponse> cmdArtifactRemovedFromUserFeed(Long artifactId, String userId) {
        CompletionStage<Okay> result =
                AskPattern.ask(
                        psCommandActor,
                        (ActorRef<Okay> replyTo) ->
                                new ShardingEnvelope<>(String.format("%d%s", artifactId, userId), new SetArtifactRemovedFromUserFeed(replyTo, artifactId, userId)),
                        timeout,
                        system.scheduler());
        return handleCommandResponse(result);
    }

    public Route psRoutes() {

        return pathPrefix("artifactState", () ->
                concat(
                        // QUERIES:
                        pathPrefix("isArtifactReadByUser", () -> concat(
                                        get(() ->
                                                parameter(StringUnmarshallers.LONG, "artifactId", artifactId ->
                                                        parameter("userId", userId -> {
                                                            CompletionStage<ExtResponse> res = queryArtifactRead(artifactId, userId);
                                                            return completeOKWithFuture(res, Jackson.marshaller());
                                                        })
                                                )
                                        ),
                                        post(() ->
                                                entity(Jackson.unmarshaller(ArtifactAndUser.class), au -> {
                                                    CompletionStage<ExtResponse> res = queryArtifactRead(au.getArtifactId(), au.getUserId());
                                                    return completeOKWithFuture(res, Jackson.marshaller());
                                                })
                                        )
                                )
                        ),
                        pathPrefix("isArtifactInUserFeed", () -> concat(
                                        get(() ->
                                                parameter(StringUnmarshallers.LONG, "artifactId", artifactId ->
                                                        parameter("userId", userId -> {
                                                            CompletionStage<ExtResponse> res = queryArtifactInUserFeed(artifactId, userId);
                                                            return completeOKWithFuture(res, Jackson.marshaller());
                                                        })
                                                )
                                        ),
                                        post(() ->
                                                entity(Jackson.unmarshaller(ArtifactAndUser.class), au -> {
                                                    CompletionStage<ExtResponse> res = queryArtifactInUserFeed(au.getArtifactId(), au.getUserId());
                                                    return completeOKWithFuture(res, Jackson.marshaller());
                                                })
                                        )
                                )
                        ),
                        pathPrefix("getAllStates", () -> concat(
                                        get(() ->
                                                parameter(StringUnmarshallers.LONG, "artifactId", artifactId ->
                                                        parameter("userId", userId -> {
                                                            CompletionStage<AllStatesResponse> res = queryAllStates(artifactId, userId);
                                                            return completeOKWithFuture(res, Jackson.marshaller());
                                                        })
                                                )
                                        ),
                                        post(() ->
                                                entity(Jackson.unmarshaller(ArtifactAndUser.class), au -> {
                                                    CompletionStage<AllStatesResponse> res = queryAllStates(au.getArtifactId(), au.getUserId());
                                                    return completeOKWithFuture(res, Jackson.marshaller());
                                                })
                                        )
                                )
                        ),

                        // commands
                        pathPrefix("setArtifactReadByUser", () -> concat(
                                        get(() ->
                                                parameter(StringUnmarshallers.LONG, "artifactId", artifactId ->
                                                        parameter("userId", userId -> {
                                                            CompletionStage<CommandResponse> res = cmdArtifactRead(artifactId, userId);
                                                            return completeOKWithFuture(res, Jackson.marshaller());
                                                        })
                                                )
                                        ),
                                        post(() ->
                                                entity(Jackson.unmarshaller(ArtifactAndUser.class), au -> {
                                                    CompletionStage<CommandResponse> res = cmdArtifactRead(au.getArtifactId(), au.getUserId());
                                                    return completeOKWithFuture(res, Jackson.marshaller());
                                                })
                                        )
                                )
                        ),
                        pathPrefix("setArtifactAddedToUserFeed", () -> concat(
                                        get(() ->
                                                parameter(StringUnmarshallers.LONG, "artifactId", artifactId ->
                                                        parameter("userId", userId -> {
                                                            CompletionStage<CommandResponse> res = cmdArtifactAddedToUserFeed(artifactId, userId);
                                                            return completeOKWithFuture(res, Jackson.marshaller());
                                                        })
                                                )
                                        ),
                                        post(() ->
                                                entity(Jackson.unmarshaller(ArtifactAndUser.class), au -> {
                                                    CompletionStage<CommandResponse> res = cmdArtifactAddedToUserFeed(au.getArtifactId(), au.getUserId());
                                                    return completeOKWithFuture(res, Jackson.marshaller());
                                                })
                                        )
                                )
                        ),
                        pathPrefix("setArtifactRemovedFromUserFeed", () -> concat(
                                        get(() ->
                                                parameter(StringUnmarshallers.LONG, "artifactId", artifactId ->
                                                        parameter("userId", userId -> {
                                                            CompletionStage<CommandResponse> res = cmdArtifactRemovedFromUserFeed(artifactId, userId);
                                                            return completeOKWithFuture(res, Jackson.marshaller());
                                                        })
                                                )
                                        ),
                                        post(() ->
                                                entity(Jackson.unmarshaller(ArtifactAndUser.class), au -> {
                                                    CompletionStage<CommandResponse> res = cmdArtifactRemovedFromUserFeed(au.getArtifactId(), au.getUserId());
                                                    return completeOKWithFuture(res, Jackson.marshaller());
                                                })
                                        )
                                )
                        )
                )
        );

    }
}
