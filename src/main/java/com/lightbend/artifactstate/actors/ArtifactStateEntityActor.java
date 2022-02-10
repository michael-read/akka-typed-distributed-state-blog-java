package com.lightbend.artifactstate.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import com.lightbend.artifactstate.serializer.EventSerializeMarker;
import com.lightbend.artifactstate.serializer.MsgSerializeMarker;

public class ArtifactStateEntityActor
        extends EventSourcedBehavior<ArtifactStateEntityActor.ArtifactCommand, ArtifactStateEntityActor.ArtifactEvent, ArtifactStateEntityActor.CurrState> {

    public final static String ARTIFACTSTATESHARDNAME = "ArtifactState";

    public interface ArtifactCommand extends MsgSerializeMarker {}
    public interface ArtifactQuery extends ArtifactCommand {}
    public interface ArtifactResponse extends MsgSerializeMarker {}

    // queries
    public static class IsArtifactReadByUser implements ArtifactQuery {
        public final ActorRef<ArtifactReadByUser> replyTo;
        public final Long artifactId;
        public final String userId;

        public IsArtifactReadByUser(ActorRef<ArtifactReadByUser> replyTo, Long artifactId, String userId) {
            this.replyTo = replyTo;
            this.artifactId = artifactId;
            this.userId = userId;
        }
    }

    public static class IsArtifactInUserFeed implements ArtifactQuery {
        public final ActorRef<ArtifactInUserFeed> replyTo;
        public final Long artifactId;
        public final String userId;

        public IsArtifactInUserFeed(ActorRef<ArtifactInUserFeed> replyTo, Long artifactId, String userId) {
            this.replyTo = replyTo;
            this.artifactId = artifactId;
            this.userId = userId;
        }
    }

    public static class GetAllStates implements ArtifactQuery {
        public final ActorRef<AllStates> replyTo;
        public final Long artifactId;
        public final String userId;

        public GetAllStates(ActorRef<AllStates> replyTo, Long artifactId, String userId) {
            this.replyTo = replyTo;
            this.artifactId = artifactId;
            this.userId = userId;
        }
    }

    // commands
    public static class SetArtifactRead implements ArtifactCommand {
        public final ActorRef<Okay> replyTo;
        public final Long artifactId;
        public final String userId;

        public SetArtifactRead(ActorRef<Okay> replyTo, Long artifactId, String userId) {
            this.replyTo = replyTo;
            this.artifactId = artifactId;
            this.userId = userId;
        }
    }

    public static class SetArtifactAddedToUserFeed implements ArtifactCommand {
        public final ActorRef<Okay> replyTo;
        public final Long artifactId;
        public final String userId;

        public SetArtifactAddedToUserFeed(ActorRef<Okay> replyTo, Long artifactId, String userId) {
            this.replyTo = replyTo;
            this.artifactId = artifactId;
            this.userId = userId;
        }
    }

    public static class SetArtifactRemovedFromUserFeed implements ArtifactCommand {
        public final ActorRef<Okay> replyTo;
        public final Long artifactId;
        public final String userId;

        public SetArtifactRemovedFromUserFeed(ActorRef<Okay> replyTo, Long artifactId, String userId) {
            this.replyTo = replyTo;
            this.artifactId = artifactId;
            this.userId = userId;
        }
    }

    // responses
    public static class Okay implements ArtifactResponse {}

    public static class ArtifactReadByUser implements ArtifactResponse {
        public final Boolean artifactRead;

        public ArtifactReadByUser(Boolean artifactRead) {
            this.artifactRead = artifactRead;
        }
    }

    public static class ArtifactInUserFeed implements ArtifactResponse {
        public final Boolean artifactInUserFeed;

        public ArtifactInUserFeed(Boolean artifactInUserFeed) {
            this.artifactInUserFeed = artifactInUserFeed;
        }
    }

    public static class AllStates implements ArtifactResponse {
        public final Boolean artifactRead;
        public final Boolean artifactInUserFeed;

        public AllStates(Boolean artifactRead, Boolean artifactInUserFeed) {
            this.artifactRead = artifactRead;
            this.artifactInUserFeed = artifactInUserFeed;
        }
    }

    // events
    public interface ArtifactEvent extends EventSerializeMarker {}
    public static class ArtifactRead implements ArtifactEvent {}
    public static class ArtifactAddedToUserFeed implements ArtifactEvent {}
    public static class ArtifactRemovedFromUserFeed implements ArtifactEvent {}

    public static class CurrState implements MsgSerializeMarker {
        Boolean artifactRead;
        Boolean artifactInUserFeed;

        CurrState(Boolean artifactRead, Boolean artifactInUserFeed) {
            this.artifactRead = artifactRead;
            this.artifactInUserFeed = artifactInUserFeed;
        }
    }

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
        return (state, event) -> {
            throw new RuntimeException("TODO: process the event return the next state");
        };
    }
}
