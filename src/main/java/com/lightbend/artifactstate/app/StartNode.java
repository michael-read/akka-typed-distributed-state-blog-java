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

    private static final Config appConfig = ConfigFactory.load();

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
            try {
                EntityTypeKey<ArtifactCommand> typeKey = EntityTypeKey.create(ArtifactCommand.class, ArtifactStateEntityActor.ARTIFACTSTATESHARDNAME);

                Cluster cluster = Cluster.get(context.getSystem());
                context.getLog().info(String.format("starting node with roles: %s", cluster.selfMember().getRoles()));

                if (cluster.selfMember().hasRole("k8s")) {
                    AkkaManagement.get(context.getSystem()).start();
                    ClusterBootstrap.get(context.getSystem()).start();
                }

                if (cluster.selfMember().hasRole("sharded")) {
context.getLog().info("starting node as sharded..");
                    ClusterSharding.get(context.getSystem()).init(
                            Entity.of(typeKey, ctx -> ArtifactStateEntityActor.create(ctx.getEntityId()))
                                    .withSettings(ClusterShardingSettings.create(context.getSystem()).withRole("sharded")));
                } else {
                    if (cluster.selfMember().hasRole("endpoint")) {
context.getLog().info("bootstrapping endpoint...");
                        ActorRef<ShardingEnvelope<ArtifactCommand>> psCommandActor =
                                ClusterSharding.get(context.getSystem()).init(
                                        Entity.of(typeKey, ctx -> ArtifactStateEntityActor.create(ctx.getEntityId())));

                        Route routes = new ArtifactStateRoutes(context.getSystem(), psCommandActor).psRoutes();
                        int httpPort = context.getSystem().settings().config().getInt("akka.http.server.default-http-port");
                        String intf = (cluster.selfMember().hasRole("docker") || cluster.selfMember().hasRole("K8s")) ? "0.0.0.0" : "localhost";
context.getLog().info(String.format("starting endpoint on interface %s:%d", intf, httpPort));

                        Function<HttpRequest, CompletionStage<HttpResponse>> grpcService =
                                ArtifactStateServiceHandlerFactory.createWithServerReflection(new GrpcArtifactStateServiceImpl(context.getSystem(), psCommandActor), context.getSystem());

                        // Create gRPC service handler
                        Route grpcHandlerRoute = handle(grpcService);

                        // As a Route
                        Route route = concat(routes, grpcHandlerRoute);

                        // Both HTTP and gRPC Binding
                        CompletionStage<ServerBinding> binding = Http.get(context.getSystem()).newServerAt(intf, httpPort).bind(route);

                        // Note: use System.out.printf to see the result of the binding
                        binding.thenApply(boundTo -> {
                            System.out.printf("HTTP / gRPC Server online at ip %s:%d", boundTo.localAddress(), httpPort);
                            return null;
                        }).exceptionally(ex -> {
                            System.out.printf("HTTP / gRPC Server binding failed at ip %s:%d\", boundTo.localAddress(), httpPort");
                            System.out.printf("exception:%s", ex.getMessage());
                            ex.printStackTrace();
                            return null;
                        });
                    }
                }
            } catch (Exception ex) {
                context.getLog().error("an exception occurred while bootstrapping node:", ex.getMessage());
                ex.printStackTrace();
            }
            return Behaviors.empty();
        });
    }

    private static CompletionStage<Done> startNode(Behavior<NotUsed> behavior, String clusterName) {
        ActorSystem<NotUsed> system = ActorSystem.create(behavior, clusterName, appConfig);
        return system.getWhenTerminated();
    }
}
