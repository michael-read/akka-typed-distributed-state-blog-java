package com.lightbend.artifactstate.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.ClusterEvent;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Subscribe;

public class ClusterListenerActor {

    private static Behavior<ClusterEvent.ClusterDomainEvent> create() {
        return Behaviors.setup(context -> {

            Cluster cluster = Cluster.get(context.getSystem());
            cluster.subscriptions().tell(new Subscribe(context.getSelf(), ClusterEvent.ClusterDomainEvent.class));

            context.getLog().info(String.format("started actor %s - %s", context.getSelf().path(), context.getSelf().getClass()));

            return Behaviors.receive(ClusterEvent.ClusterDomainEvent.class)
                    .onMessage(ClusterEvent.MemberUp.class, event -> {
                        context.getLog().info(String.format("Member is Up: %s", event.member()));
                        return Behaviors.same();
                    })
                    .onMessage(ClusterEvent.UnreachableMember.class, event -> {
                        context.getLog().info(String.format("Member detected as unreachable: %s", event.member()));
                        return Behaviors.same();
                    })
                    .onMessage(ClusterEvent.MemberRemoved.class, event -> {
                        context.getLog().info(String.format("Member is Removed: %s", event.member()));
                        return Behaviors.same();
                    })
                    .build();
        });
    }
}
