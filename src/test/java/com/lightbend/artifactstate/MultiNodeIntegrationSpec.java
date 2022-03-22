package com.lightbend.artifactstate;

import akka.actor.CoordinatedShutdown;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorSystem;
import akka.cluster.MemberStatus;
import akka.cluster.typed.Cluster;
import akka.grpc.GrpcClientSettings;
import akka.testkit.SocketUtil;
import com.lightbend.artifactstate.app.StartNode;
import com.lightbend.artifactstate.endpoint.ArtifactStateProto.CommandResponse;
import com.lightbend.artifactstate.endpoint.ArtifactStateProto.ArtifactAndUser;
import com.lightbend.artifactstate.endpoint.ArtifactStateProto.ExtResponse;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightbend.artifactstate.endpoint.ArtifactStateServiceClient;
import scala.jdk.CollectionConverters;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MultiNodeIntegrationSpec {
    private static final Logger logger = LoggerFactory.getLogger(MultiNodeIntegrationSpec.class);

    private static Config sharedConfig() {
        return ConfigFactory.load("multinode-test-cassandra.conf");
    }

    private static Config nodeConfig() {
        return ConfigFactory.parseString(
                "akka.cluster {"
                        + "\n"
                        + "roles=[\"sharded\", \"k8s\"]"
                + "\n"
                + "}"
                + "\n"
                + "akka.persistence {"
                    + "\n"
                    + "journal.plugin = \"akka.persistence.cassandra.journal\""
                    + "\n"
                    + "snapshot-store.plugin = \"akka.persistence.cassandra.snapshot\""
                    + "\n"
                + "}"
                + "\n"
//              # NOTE: autocreation of journal and snapshot should not be used in production
                + "akka.persistence.cassandra {"
                    + "\n"
                    + "journal {"
                        + "\n"
                        + "keyspace-autocreate = true"
                        + "\n"
                        + "tables-autocreate = true"
                        + "\n"
                    + "}"
                    + "\n"
                    + "snapshot {"
                        + "\n"
                        + "keyspace-autocreate = true"
                        + "\n"
                        + "tables-autocreate = true"
                        + "\n"
                    + "}"
                    + "\n"
                + "}"
                + "\n"
                + "datastax-java-driver {"
                    + "\n"
                    + "advanced.reconnect-on-init = true"
                    + "\n"
                    + "basic.contact-points = [\"127.0.0.1:9042\"]"
                    + "\n"
                    + "basic.load-balancing-policy.local-datacenter = \"datacenter1\""
                    + "\n"
                + "}"
                + "\n"
        );
    }

    private static Config endpointContig(int grcpPort) {
        return ConfigFactory.parseString(
        "akka.http.server.default-http-port="
            + grcpPort
            + "\n"
            + "akka.http.server.preview.enable-http2 = on"
            + "\n"
            + "akka.cluster {"
                    + "\n"
                    + "roles=[\"endpoint\", \"k8s\"]"
            + "\n"
            + "}"
        );
    }

    private static Config clusterBootstrapConfig(List<Integer> managementPorts, int managementPortIndex) {
        return ConfigFactory.parseString(
                "akka.management.http.hostname = 127.0.0.1"
                        + "\n"
                        + "akka.management.http.port = "
                        + managementPorts.get(managementPortIndex)
                        + "\n"
                        + "akka.discovery.config.services.ArtifactStateCluster.endpoints = [\n"
                        + "  { host = \"127.0.0.1\", port = "
                        + managementPorts.get(0)
                        + "},\n"
                        + "  { host = \"127.0.0.1\", port = "
                        + managementPorts.get(1)
                        + "},\n"
                        + "  { host = \"127.0.0.1\", port = "
                        + managementPorts.get(2)
                        + "},\n"
                        + "]"
        );
    }
    
    private static TestNodeFixture testNode1;
    private static TestNodeFixture testNode2;
    private static TestEndpointFixture endpointNode3;
    private static List<ActorSystem<?>> systems;
    private static final Duration requestTimeout = Duration.ofSeconds(10);

    @BeforeClass
    public static void setup() throws Exception {
        logger.info("setup started...");
        List<InetSocketAddress> inetSocketAddresses =
                CollectionConverters.SeqHasAsJava(
                                SocketUtil.temporaryServerAddresses(6, "127.0.0.1", false))
                        .asJava();

        // we're really only using the gRPC port on the endpoint.
/*        List<Integer> grpcPorts =
                inetSocketAddresses.subList(0, 3).stream()
                        .map(InetSocketAddress::getPort)
                        .collect(Collectors.toList());*/
        List<Integer> managementPorts =
                inetSocketAddresses.subList(3, 6).stream()
                        .map(InetSocketAddress::getPort)
                        .collect(Collectors.toList());
//        List<Integer> managementPorts = Arrays.asList(8558,8559,8560);

        logger.info("management ports:" + managementPorts.toString());

        testNode1 = new TestNodeFixture(managementPorts, 0);
        testNode2 = new TestNodeFixture(managementPorts, 1);
        endpointNode3 = new TestEndpointFixture(8082, managementPorts, 2);
        systems = Arrays.asList(testNode1.system, testNode2.system, endpointNode3.system);

        testNode1.testKit.spawn(StartNode.rootBehavior());
        testNode2.testKit.spawn(StartNode.rootBehavior());
        endpointNode3.testKit.spawn(StartNode.rootBehavior());

        // wait for all nodes to have joined the cluster, become up and see all other nodes as up
        TestProbe<Object> upProbe = testNode1.testKit.createTestProbe();
        systems.forEach(
                system -> {
                    upProbe.awaitAssert(
                            Duration.ofSeconds(15),
                            () -> {
                                Cluster cluster = Cluster.get(system);
                                assertEquals(MemberStatus.up(), cluster.selfMember().status());
                                cluster
                                        .state()
                                        .getMembers()
                                        .iterator()
                                        .forEachRemaining(member -> assertEquals(MemberStatus.up(), member.status()));
                                return null;
                            });
                });
        logger.info("setup completed...");
    }

    @AfterClass
    public static void tearDown() {
        logger.info("tearDown started...");
        endpointNode3.testKit.shutdownTestKit();
        testNode2.testKit.shutdownTestKit();
        testNode1.testKit.shutdownTestKit();
        logger.info("tearDown completed...");
    }

    private static class TestNodeFixture {
        private final ActorTestKit testKit;
        private final ActorSystem<?> system;

        public TestNodeFixture(List<Integer> managementPorts, int managementPortIndex) {
            testKit =
                    ActorTestKit.create(
                            "ArtifactStateCluster",
                            nodeConfig()
                                    .withFallback(clusterBootstrapConfig(managementPorts, managementPortIndex))
                                    .withFallback(sharedConfig()));
            system = testKit.system();
        }

    }

    private static class TestEndpointFixture {

        private final ActorTestKit testKit;
        private final ActorSystem<?> system;

        private final GrpcClientSettings clientSettings;
        private ArtifactStateServiceClient client = null;

        private TestEndpointFixture(int grcpPort, List<Integer> managementPorts, int managementPortIndex) {
            testKit =
                    ActorTestKit.create(
                            "ArtifactStateCluster",
                            endpointContig(grcpPort)
                                    .withFallback(clusterBootstrapConfig(managementPorts, managementPortIndex))
                                    .withFallback(sharedConfig()));
            system = testKit.system();
            clientSettings =
                    GrpcClientSettings.connectToServiceAt("127.0.0.1", grcpPort, system).withTls(false);
        }

        public ArtifactStateServiceClient getClient() {
            if (client == null) {
                client = ArtifactStateServiceClient.create(clientSettings, system);
                CoordinatedShutdown.get(system)
                        .addTask(
                                CoordinatedShutdown.PhaseBeforeServiceUnbind(),
                                "close-test-client-for-grpc",
                                () -> client.close());
            }
            return client;
        }
    }

    // the following test leverage protobuf / grpcService
    @Test
    public void testAllViaGrpc() throws Exception {
        logger.info("testAllViaGrpc started...");

        ArtifactAndUser michael1 = ArtifactAndUser.newBuilder()
                .setUserId("Michael")
                .setArtifactId(1)
                .build();

        // setArtifactReadByUser
        CompletionStage<CommandResponse> response1 =
                endpointNode3
                        .getClient()
                        .setArtifactReadByUser()
                        .invoke(michael1);
        CommandResponse commandResponse = response1.toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertTrue(commandResponse.getSuccess());

        // isArtifactReadByUser
        CompletionStage<ExtResponse> response2 =
                endpointNode3
                        .getClient()
                        .isArtifactReadByUser()
                        .invoke(michael1);
        ExtResponse extResponse = response2.toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertEquals(extResponse.getUserId(), michael1.getUserId());
        assertEquals(extResponse.getArtifactId(), michael1.getArtifactId());
        assertTrue(extResponse.getAnswer());

        logger.info("testAllViaGrpc completed...");
    }
}
