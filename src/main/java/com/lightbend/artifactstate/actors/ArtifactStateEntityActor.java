package com.lightbend.artifactstate.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.lightbend.artifactstate.serializer.EventSerializeMarker;
import com.lightbend.artifactstate.serializer.MsgSerializeMarker;

public class ArtifactStateEntityActor
        extends EventSourcedBehavior<ArtifactStateEntityActor.ArtifactCommand, ArtifactStateEntityActor.ArtifactEvent, ArtifactStateEntityActor.CurrState> {

    public final static String ARTIFACTSTATESHARDNAME = "ArtifactState";

    public interface ArtifactCommand extends MsgSerializeMarker {}
    public interface ArtifactQuery extends ArtifactCommand {}
    public interface ArtifactResponse extends MsgSerializeMarker {}

    // queries
    public static record IsArtifactReadByUser(@JsonProperty("replyTo") ActorRef<ArtifactReadByUser> replyTo, @JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId) implements ArtifactQuery {}

    public static record IsArtifactInUserFeed(@JsonProperty("replyTo") ActorRef<ArtifactInUserFeed> replyTo, @JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId) implements ArtifactQuery {}

    public static record GetAllStates(@JsonProperty("replyTo") ActorRef<AllStates> replyTo, @JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId) implements ArtifactQuery {}

    // commands
    public static record SetArtifactRead(@JsonProperty("replyTo") ActorRef<Okay> replyTo, @JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId) implements ArtifactCommand {}

    public static record SetArtifactAddedToUserFeed(@JsonProperty("replyTo") ActorRef<Okay> replyTo, @JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId) implements ArtifactCommand {}

    public static record SetArtifactRemovedFromUserFeed(@JsonProperty("replyTo") ActorRef<Okay> replyTo, @JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId) implements ArtifactCommand {}

    // responses
    @JsonSerialize
    public static record Okay() implements ArtifactResponse {}

    public static record ArtifactReadByUser(@JsonProperty("artifactRead")  Boolean artifactRead) implements ArtifactResponse {}

    public static record ArtifactInUserFeed(@JsonProperty("artifactRead") Boolean artifactInUserFeed) implements ArtifactResponse {}

    public static record AllStates(@JsonProperty("artifactRead") Boolean artifactRead, @JsonProperty("artifactInUserFeed") Boolean artifactInUserFeed) implements ArtifactResponse {}

    // events
    public static interface ArtifactEvent extends EventSerializeMarker {}
    @JsonSerialize
    public static record ArtifactRead() implements ArtifactEvent {}
    @JsonSerialize
    public static record ArtifactAddedToUserFeed() implements ArtifactEvent {}
    @JsonSerialize
    public static record ArtifactRemovedFromUserFeed() implements ArtifactEvent {}

    public static record CurrState(Boolean artifactRead, Boolean artifactInUserFeed) implements MsgSerializeMarker {}

    public static Behavior<ArtifactCommand> create(String entityId) {
        return Behaviors.setup(context -> new ArtifactStateEntityActor(entityId));
    }

    private ArtifactStateEntityActor(String entityId) {
        super(PersistenceId.apply(ARTIFACTSTATESHARDNAME, entityId));
    }

    @Override
    public CurrState emptyState() {
        return new CurrState(false, false);
    }

    @Override
    public CommandHandler<ArtifactCommand, ArtifactEvent, CurrState> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                // commands
                .onCommand(SetArtifactRead.class, this::artifactRead)
                .onCommand(SetArtifactAddedToUserFeed.class, this::artifactAddedToUserFeed)
                .onCommand(SetArtifactRemovedFromUserFeed.class, this::artifactRemovedFromUserFeed)

                // queries
                .onCommand(IsArtifactReadByUser.class, this::getArtifactRead)
                .onCommand(IsArtifactInUserFeed.class, this::getAritfactInFeed)
                .onCommand(GetAllStates.class, this::getArtifactState)
                .build();
    }

    // commands
    private Effect<ArtifactEvent, CurrState> artifactRead(SetArtifactRead command) {
        return Effect()
                .persist(new ArtifactRead())
                .thenRun(newState -> command.replyTo.tell(new Okay()));
    }

    private Effect<ArtifactEvent, CurrState> artifactAddedToUserFeed(SetArtifactAddedToUserFeed command) {
        return Effect()
                .persist(new ArtifactAddedToUserFeed())
                .thenRun(newState -> command.replyTo.tell(new Okay()));
    }

    private Effect<ArtifactEvent, CurrState> artifactRemovedFromUserFeed(SetArtifactRemovedFromUserFeed command) {
        return Effect()
                .persist(new ArtifactRemovedFromUserFeed())
                .thenRun(newState -> command.replyTo.tell(new Okay()));
    }

    // queries
    private Effect<ArtifactEvent, CurrState> getArtifactRead(IsArtifactReadByUser command) {
        return Effect()
                .none()
                .thenRun(newState -> command.replyTo.tell(new ArtifactReadByUser(newState.artifactRead)));
    }

    private Effect<ArtifactEvent, CurrState> getAritfactInFeed(IsArtifactInUserFeed command) {
        return Effect()
                .none()
                .thenRun(newState -> command.replyTo.tell(new ArtifactInUserFeed(newState.artifactInUserFeed)));
    }

    private Effect<ArtifactEvent, CurrState> getArtifactState(GetAllStates command) {
        return Effect()
                .none()
                .thenRun(newState -> command.replyTo.tell(new AllStates(newState.artifactRead, newState.artifactInUserFeed)));
    }

    @Override
    public EventHandler<CurrState, ArtifactEvent> eventHandler() {
        EventHandlerBuilder<CurrState, ArtifactEvent> builder = newEventHandlerBuilder();
        builder.forStateType(CurrState.class)
                .onEvent(ArtifactRead.class, (state, event) -> new CurrState(true, state.artifactInUserFeed))
                .onEvent(ArtifactAddedToUserFeed.class, (state, event) -> new CurrState(state.artifactRead, true))
                .onEvent(ArtifactRemovedFromUserFeed.class, (state, event) -> new CurrState(state.artifactRead, false))
                .onAnyEvent((state, event) -> {
                    throw new IllegalStateException(String.format("unexpected event %s in state %s", event.getClass().getName(), state.toString()));
                });
        return builder.build();
    }
}
