package com.lightbend.artifactstate.app;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.ClusterShardingSettings;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.cluster.typed.Cluster;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.japi.function.Function;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import akka.persistence.typed.ReplicaId;
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor;
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor.ArtifactCommand;
import com.lightbend.artifactstate.endpoint.ArtifactStateRoutes;
import com.lightbend.artifactstate.endpoint.ArtifactStateServiceHandlerFactory;
import com.lightbend.artifactstate.endpoint.GrpcArtifactStateServiceImpl;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.concat;
import static akka.http.javadsl.server.Directives.handle;

public class StartNode {
    private static final Config appConfig = ConfigFactory.load();

    public static void main(String[] args) {
        String clusterName = appConfig.getString("clustering.cluster.name");

        ReplicaId dataCenter = (appConfig.hasPath("akka.cluster.multi-data-center.self-data-center")) ?
            new ReplicaId(appConfig.getString("akka.cluster.multi-data-center.self-data-center")) : new ReplicaId("dc-default");

        Set<ReplicaId> dcsConfigs = new HashSet<>();
        if (appConfig.hasPath("clustering.allDataCenters")) {
            Arrays.stream(appConfig.getString("clustering.allDataCenters").split(",")).forEach ( dc ->
                dcsConfigs.add(new ReplicaId(dc)));
        }
        String queryPluginId = appConfig.getString("clustering.queryPluginId");

        if (appConfig.hasPath("clustering.ports")) {
            List<Integer> clusterPorts = appConfig.getIntList("clustering.ports");
            clusterPorts.forEach(port -> startNode(rootBehavior(dataCenter, dcsConfigs, queryPluginId), clusterName));
        }
        else {
            startNode(rootBehavior(dataCenter, dcsConfigs, queryPluginId), clusterName);
        }
    }

//    public static Behavior<NotUsed> rootBehavior(int port, int defaultPort) {
    public static Behavior<NotUsed> rootBehavior(ReplicaId dataCenter, Set<ReplicaId> allDataCenters, String queryPluginId) {
        return Behaviors.setup(context -> {
            try {
                context.getLog().info(String.format("init RootBehavior: data center: %s", dataCenter));
                context.getLog().info(String.format("init RootBehavior: all data centers : %s", allDataCenters));
                context.getLog().info(String.format("init RootBehavior: queryPluginId: %s", queryPluginId));

                EntityTypeKey<ArtifactCommand> typeKey = EntityTypeKey.create(ArtifactCommand.class, ArtifactStateEntityActor.ARTIFACTSTATESHARDNAME);

                Cluster cluster = Cluster.get(context.getSystem());
                context.getLog().info(String.format("starting node with roles: %s", cluster.selfMember().getRoles()));

                if (cluster.selfMember().hasRole("k8s") || cluster.selfMember().hasRole("dns")) {
                    AkkaManagement.get(context.getSystem()).start();
                    ClusterBootstrap.get(context.getSystem()).start();
                }

                if (cluster.selfMember().hasRole("sharded")) {
context.getLog().info("starting node as sharded..");
                    ClusterSharding.get(context.getSystem()).init(
                            Entity.of(typeKey, ctx -> ArtifactStateEntityActor.create(ctx.getEntityId(), dataCenter, allDataCenters, queryPluginId))
                                    .withSettings(ClusterShardingSettings.create(context.getSystem()).withRole("sharded"))
                                    .withDataCenter(dataCenter.id()));
                } else {
                    if (cluster.selfMember().hasRole("endpoint")) {
context.getLog().info("bootstrapping endpoint...");
                        ActorRef<ShardingEnvelope<ArtifactCommand>> psCommandActor =
                                ClusterSharding.get(context.getSystem()).init(
                                        Entity.of(typeKey, ctx -> ArtifactStateEntityActor.create(ctx.getEntityId(), dataCenter, allDataCenters, queryPluginId))
                                                .withDataCenter(dataCenter.id()));

                        Route routes = new ArtifactStateRoutes(context.getSystem(), psCommandActor).psRoutes();
                        int httpPort = context.getSystem().settings().config().getInt("akka.http.server.default-http-port");
                        String intf = (cluster.selfMember().hasRole("docker") || cluster.selfMember().hasRole("k8s") || cluster.selfMember().hasRole("dns")) ? "0.0.0.0" : "localhost";
context.getLog().info(String.format("starting endpoint on interface %s:%d", intf, httpPort));

                        Function<HttpRequest, CompletionStage<HttpResponse>> grpcService =
                                ArtifactStateServiceHandlerFactory.createWithServerReflection(new GrpcArtifactStateServiceImpl(context.getSystem(), psCommandActor), context.getSystem());

                        // Create gRPC service handler
                        Route grpcHandlerRoute = handle(grpcService);

                        // As a Route
                        Route route = concat(routes, grpcHandlerRoute);

                        // Both HTTP and gRPC Binding
                        CompletionStage<ServerBinding> binding = Http.get(context.getSystem()).newServerAt(intf, httpPort).bind(route);

                        // Note: use the actorsystem's global logger because the context is gone by the time this happens.
                        binding.thenApply(boundTo -> {
                            context.getSystem().log().info(String.format("HTTP / gRPC Server online at ip %s\n", boundTo.localAddress()));
                            return null;
                        }).exceptionally(ex -> {
                            context.getSystem().log().error(String.format("HTTP Server binding failed at %s:%d\n", intf, httpPort));
                            context.getSystem().log().error(String.format("exception:%s", ex.getMessage()), ex);
                            ex.printStackTrace();
                            return null;
                        });
                    }
                }
            } catch (Exception ex) {
                context.getLog().error("an exception occurred while bootstrapping node: %s", ex.getMessage());
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
