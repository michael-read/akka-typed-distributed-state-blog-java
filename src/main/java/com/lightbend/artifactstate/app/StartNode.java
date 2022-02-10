package com.lightbend.artifactstate.app;

import akka.Done;
import akka.NotUsed;

import akka.cluster.sharding.typed.ClusterShardingSettings;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import static akka.http.javadsl.server.Directives.*;

import akka.cluster.typed.Cluster;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor;
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor.ArtifactCommand;
import com.lightbend.artifactstate.endpoint.ArtifactStateRoutes;
import com.lightbend.artifactstate.endpoint.ArtifactStateServiceHandlerFactory;
import com.lightbend.artifactstate.endpoint.GrpcArtifactStateServiceImpl;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class StartNode {

    private static Config appConfig = ConfigFactory.load();

    public static void main(String[] args) {
        String clusterName = appConfig.getString("clustering.cluster.name");
        int clusterPort = appConfig.getInt("clustering.port");
        int defaultPort = appConfig.getInt("clustering.defaultPort");
        if (appConfig.hasPath("clustering.ports")) {
            List<Integer> clusterPorts = appConfig.getIntList("clustering.ports");
            clusterPorts.forEach(port -> {
                startNode(rootBehavior(port, defaultPort), clusterName);
            });
        }
        else {
            startNode(rootBehavior(clusterPort, defaultPort), clusterName);
        }
    }

    private static Behavior<NotUsed> rootBehavior(int port, int defaultPort) {
        return Behaviors.setup(context -> {
            akka.actor.ActorSystem classicSystem = context.getSystem().classicSystem();

            EntityTypeKey<ArtifactCommand> typeKey = EntityTypeKey.create(ArtifactCommand.class, ArtifactStateEntityActor.ARTIFACTSTATESHARDNAME);

            Cluster cluster = Cluster.get(context.getSystem());
            context.getLog().info(String.format("starting node with roles: %s", cluster.selfMember().getRoles()));

            if (cluster.selfMember().hasRole("k8s")) {
                AkkaManagement.get(classicSystem).start();
                ClusterBootstrap.get(classicSystem).start();
            }

            ClusterSharding sharding = ClusterSharding.get(context.getSystem());
            if (cluster.selfMember().hasRole("sharded")) {
                sharding.init(Entity.of(typeKey, ctx -> ArtifactStateEntityActor.create(ctx.getEntityId()))
                        .withSettings(ClusterShardingSettings.create(context.getSystem()).withRole("backend")));
            }
            else {
                if (cluster.selfMember().hasRole("endpoint")) {
                    ActorRef<ShardingEnvelope<ArtifactCommand>> psCommandActor =
                            sharding.init(
                                    Entity.of(typeKey, ctx -> ArtifactStateEntityActor.create(ctx.getEntityId())));

                    Route routes = new ArtifactStateRoutes(context.getSystem(), psCommandActor).psRoutes();
                    int httpPort = context.getSystem().settings().config().getInt("akka.http.server.default-http-port");
                    String intf = (cluster.selfMember().hasRole("docker") || cluster.selfMember().hasRole("K8s")) ? "0.0.0.0" : "localhost";

                    Function<HttpRequest, CompletionStage<HttpResponse>> grpcService =
                            ArtifactStateServiceHandlerFactory.create(new GrpcArtifactStateServiceImpl(context.getSystem(), psCommandActor), classicSystem);

                    // Create gRPC service handler
                    Route grpcHandlerRoute = handle(grpcService);

                    // As a Route
                    Route route = concat(routes, grpcHandlerRoute);

                    // Both HTTP and gRPC Binding
                    CompletionStage<ServerBinding> binding = Http.get(classicSystem).newServerAt(intf, httpPort).bind(route);
                    binding.thenApply(boundTo -> {
                        context.getLog().info(String.format("HTTP / gRPC Server online at ip %s:%d", boundTo.localAddress(), httpPort));
                        return null;
                    });
                }

            }
            return Behaviors.empty();
        });
    }

    private static CompletionStage<Done> startNode(Behavior<NotUsed> behavior, String clusterName) {
        ActorSystem<NotUsed> system = ActorSystem.create(behavior, clusterName, appConfig);
        return system.getWhenTerminated();
    }
}
