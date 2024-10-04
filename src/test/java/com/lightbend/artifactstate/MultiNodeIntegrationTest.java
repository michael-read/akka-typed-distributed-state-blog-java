package com.lightbend.artifactstate;

import akka.actor.CoordinatedShutdown;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorSystem;
import akka.cluster.MemberStatus;
import akka.cluster.typed.Cluster;
import akka.grpc.GrpcClientSettings;
import akka.http.javadsl.Http;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.testkit.SocketUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbend.artifactstate.app.StartNode;
import com.lightbend.artifactstate.endpoint.ArtifactStatePocAPI;
import com.lightbend.artifactstate.endpoint.ArtifactStateProto;
import com.lightbend.artifactstate.endpoint.ArtifactStateProto.ArtifactAndUser;
import com.lightbend.artifactstate.endpoint.ArtifactStateProto.CommandResponse;
import com.lightbend.artifactstate.endpoint.ArtifactStateProto.ExtResponse;
import com.lightbend.artifactstate.endpoint.ArtifactStateServiceClient;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.jdk.CollectionConverters;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;

public class MultiNodeIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(MultiNodeIntegrationTest.class);

    private static Config sharedConfig() {
        return ConfigFactory.load("multinode-test.conf");
    }

    private static Config nodeConfig() {
        return ConfigFactory.parseString(
                """
                        akka.cluster {\
                        
                        roles=["sharded", "k8s"]\
                        
                        }\
                        
                        """
        ).withFallback(persistenceConfig());
    }

    /*
    Using Cassandra with Akka Persistence by default. If you want to use Yugabyte instead, please see configuration below.
     */
    private static Config persistenceConfig() {
        return ConfigFactory.parseString(
                "akka.persistence {"
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

    /*
    Yugabyte R2DBC Persistence driver. Comment out Cassandra above, and uncomment the following to use Yugabyte.
     */
/*
    private static Config persistenceConfig() {
        return ConfigFactory.parseString(
            "akka.persistence {\n"
                + "journal.plugin = \"akka.persistence.r2dbc.journal\"\n"
                + "snapshot-store.plugin = \"akka.persistence.r2dbc.snapshot\"\n"
                + "state.plugin = \"akka.persistence.r2dbc.durable-state-store\"\n"
                + "r2dbc {\n"
                    + "dialect = \"yugabyte\"\n"
                    + "connection-factory {\n"
                        + "driver = \"postgres\"\n"
                        + "host = \"127.0.0.1\"\n"
                        + "port = 5433\n"
                        + "database = \"yugabyte\"\n"
                        + "user = \"yugabyte\"\n"
                        + "password = \"yugabyte\"\n"
                    + "}\n"
                + "}\n"
            + "}\n"
        );
    }
*/

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
    private static final Duration requestTimeout = Duration.ofSeconds(10);

    @BeforeClass
    public static void setup() {
        logger.info("setup started...");

        // grab six temporary ports
        List<InetSocketAddress> inetSocketAddresses =
                CollectionConverters.SeqHasAsJava(
                                SocketUtil.temporaryServerAddresses(6, "127.0.0.1", false))
                        .asJava();

        // setup unique management ports
        List<Integer> managementPorts =
                inetSocketAddresses.subList(3, 6).stream()
                        .map(InetSocketAddress::getPort)
                        .collect(Collectors.toList());

        logger.info("management ports:{}", managementPorts);

        testNode1 = new TestNodeFixture(managementPorts, 0);
        testNode2 = new TestNodeFixture(managementPorts, 1);
        endpointNode3 = new TestEndpointFixture(8082, managementPorts, 2);
        List<ActorSystem<?>> systems = Arrays.asList(testNode1.system, testNode2.system, endpointNode3.system);

        int defaultPort = managementPorts.get(0);
        int node2Port = managementPorts.get(1);
        int endpointPort = managementPorts.get(2);

        testNode1.testKit.spawn(StartNode.rootBehavior(defaultPort, defaultPort));
        testNode2.testKit.spawn(StartNode.rootBehavior(node2Port, defaultPort));
        endpointNode3.testKit.spawn(StartNode.rootBehavior(endpointPort, defaultPort));

        // wait for all nodes to have joined the cluster, become up and see all other nodes as up
        TestProbe<Object> upProbe = testNode1.testKit.createTestProbe();
        systems.forEach(
                system -> upProbe.awaitAssert(
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
                        }));
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

        public ArtifactStateServiceClient getGrpcClient() {
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
                        .getGrpcClient()
                        .setArtifactReadByUser()
                        .invoke(michael1);
        CommandResponse commandResponse1 = response1.toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertTrue(commandResponse1.getSuccess());

        // isArtifactReadByUser
        CompletionStage<ExtResponse> response2 =
                endpointNode3
                        .getGrpcClient()
                        .isArtifactReadByUser()
                        .invoke(michael1);
        ExtResponse extResponse1 = response2.toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertEquals(extResponse1.getUserId(), michael1.getUserId());
        assertEquals(extResponse1.getArtifactId(), michael1.getArtifactId());
        assertTrue(extResponse1.getAnswer());

        // setArtifactAddedToUserFeed
        CompletionStage<CommandResponse> response3 =
                endpointNode3
                        .getGrpcClient()
                        .setArtifactAddedToUserFeed()
                        .invoke(michael1);
        CommandResponse commandResponse2 = response3.toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertTrue(commandResponse2.getSuccess());

        // isArtifactInUserFeed
        CompletionStage<ExtResponse> response4 =
                endpointNode3
                        .getGrpcClient()
                        .isArtifactInUserFeed()
                        .invoke(michael1);
        ExtResponse extResponse2 = response4.toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertEquals(extResponse2.getUserId(), michael1.getUserId());
        assertEquals(extResponse2.getArtifactId(), michael1.getArtifactId());
        assertTrue(extResponse2.getAnswer());

        // setArtifactAddedToUserFeed
        CompletionStage<CommandResponse> response5 =
                endpointNode3
                        .getGrpcClient()
                        .setArtifactAddedToUserFeed()
                        .invoke(michael1);
        CommandResponse commandResponse3 = response5.toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertTrue(commandResponse3.getSuccess());

        // isArtifactInUserFeed
        CompletionStage<ExtResponse> response6 =
                endpointNode3
                        .getGrpcClient()
                        .isArtifactInUserFeed()
                        .invoke(michael1);
        ExtResponse extResponse3 = response6.toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertEquals(extResponse3.getUserId(), michael1.getUserId());
        assertEquals(extResponse3.getArtifactId(), michael1.getArtifactId());
        assertTrue(extResponse3.getAnswer());

        // setArtifactRemovedFromUserFeed
        CompletionStage<CommandResponse> response7 =
                endpointNode3
                        .getGrpcClient()
                        .setArtifactRemovedFromUserFeed()
                        .invoke(michael1);
        CommandResponse commandResponse4 = response7.toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertTrue(commandResponse4.getSuccess());

        // isArtifactInUserFeed
        CompletionStage<ExtResponse> response8 =
                endpointNode3
                        .getGrpcClient()
                        .isArtifactInUserFeed()
                        .invoke(michael1);
        ExtResponse extResponse4 = response8.toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertEquals(extResponse4.getUserId(), michael1.getUserId());
        assertEquals(extResponse4.getArtifactId(), michael1.getArtifactId());
        assertFalse(extResponse4.getAnswer());

        CompletionStage<ArtifactStateProto.AllStatesResponse> response9 =
                endpointNode3
                        .getGrpcClient()
                        .getAllStates()
                        .invoke(michael1);
        ArtifactStateProto.AllStatesResponse state = response9.toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertEquals(state.getUserId(), michael1.getUserId());
        assertEquals(state.getArtifactId(), michael1.getArtifactId());
        assertTrue(state.getArtifactRead());
        assertFalse(state.getArtifactInUserFeed());

        logger.info("testAllViaGrpc completed...");
    }

    @Test
    public void testAllViaHttpPOST() throws Exception {
        logger.info("testAllViaHttpPOST started...");

        final ArtifactStatePocAPI.ArtifactAndUser artifactAndUser = new ArtifactStatePocAPI.ArtifactAndUser(2L, "Michael");
        final ObjectMapper mapper = new ObjectMapper();
        final String michael2 = mapper.writeValueAsString(artifactAndUser);

        /// setArtifactReadByUser
        final HttpResponse response1 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.POST("http://localhost:8082/artifactState/setArtifactReadByUser")
                                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, michael2)))
                .toCompletableFuture()
                .get(requestTimeout.getSeconds(), SECONDS);
        final ArtifactStatePocAPI.CommandResponse commandResponse1 = Jackson.unmarshaller(ArtifactStatePocAPI.CommandResponse.class)
                .unmarshal(response1.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);

        assertTrue(commandResponse1.success());

        // isArtifactReadByUser
        final HttpResponse response2 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.POST("http://localhost:8082/artifactState/isArtifactReadByUser")
                                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, michael2)))
                        .toCompletableFuture()
                        .get(requestTimeout.getSeconds(), SECONDS);

        final ArtifactStatePocAPI.ExtResponse extResponse1 = Jackson.unmarshaller(ArtifactStatePocAPI.ExtResponse.class)
                .unmarshal(response2.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertEquals(extResponse1.artifactId(), artifactAndUser.artifactId());
        assertEquals(extResponse1.userId(), artifactAndUser.userId());
        assertTrue(extResponse1.answer());

        // setArtifactAddedToUserFeed
        final HttpResponse response3 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.POST("http://localhost:8082/artifactState/setArtifactAddedToUserFeed")
                                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, michael2)))
                        .toCompletableFuture()
                        .get(requestTimeout.getSeconds(), SECONDS);
        final ArtifactStatePocAPI.CommandResponse commandResponse2 = Jackson.unmarshaller(ArtifactStatePocAPI.CommandResponse.class)
                .unmarshal(response3.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);

        assertTrue(commandResponse2.success());

        // isArtifactInUserFeed
        final HttpResponse response4 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.POST("http://localhost:8082/artifactState/isArtifactInUserFeed")
                                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, michael2)))
                        .toCompletableFuture()
                        .get(requestTimeout.getSeconds(), SECONDS);

        final ArtifactStatePocAPI.ExtResponse extResponse2 = Jackson.unmarshaller(ArtifactStatePocAPI.ExtResponse.class)
                .unmarshal(response4.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertSame(extResponse2.artifactId(), artifactAndUser.artifactId());
        assertEquals(extResponse2.userId(), artifactAndUser.userId());
        assertTrue(extResponse2.answer());
        
        // setArtifactRemovedFromUserFeed
        final HttpResponse response5 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.POST("http://localhost:8082/artifactState/setArtifactRemovedFromUserFeed")
                                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, michael2)))
                        .toCompletableFuture()
                        .get(requestTimeout.getSeconds(), SECONDS);
        final ArtifactStatePocAPI.CommandResponse commandResponse3 = Jackson.unmarshaller(ArtifactStatePocAPI.CommandResponse.class)
                .unmarshal(response5.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);

        assertTrue(commandResponse3.success());

        // getAllStates
        final HttpResponse response6 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.POST("http://localhost:8082/artifactState/getAllStates")
                                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, michael2)))
                        .toCompletableFuture()
                        .get(requestTimeout.getSeconds(), SECONDS);

        final ArtifactStatePocAPI.AllStatesResponse allStatesResponse = Jackson.unmarshaller(ArtifactStatePocAPI.AllStatesResponse.class)
                .unmarshal(response6.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertSame(allStatesResponse.artifactId(), artifactAndUser.artifactId());
        assertEquals(allStatesResponse.userId(), artifactAndUser.userId());
        assertTrue(allStatesResponse.artifactRead());
        assertFalse(allStatesResponse.artifactInUserFeed());

        logger.info("testAllViaHttpPOST completed...");
    }

    @Test
    public void testAllViaHttpGET() throws Exception {
        logger.info("testAllViaHttpGET started...");

        final ArtifactStatePocAPI.ArtifactAndUser artifactAndUser = new ArtifactStatePocAPI.ArtifactAndUser(3L, "Michael");
        final String michael3Params = String.format("artifactId=%d&userId=%s", artifactAndUser.artifactId(), artifactAndUser.userId());

        /// setArtifactReadByUser
        final HttpResponse response1 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.GET("http://localhost:8082/artifactState/setArtifactReadByUser?" + michael3Params))
                        .toCompletableFuture()
                        .get(requestTimeout.getSeconds(), SECONDS);
        final ArtifactStatePocAPI.CommandResponse commandResponse1 = Jackson.unmarshaller(ArtifactStatePocAPI.CommandResponse.class)
                .unmarshal(response1.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);

        assertTrue(commandResponse1.success());

        // isArtifactReadByUser
        final HttpResponse response2 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.GET("http://localhost:8082/artifactState/isArtifactReadByUser?" + michael3Params))
                        .toCompletableFuture()
                        .get(requestTimeout.getSeconds(), SECONDS);

        final ArtifactStatePocAPI.ExtResponse extResponse1 = Jackson.unmarshaller(ArtifactStatePocAPI.ExtResponse.class)
                .unmarshal(response2.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertEquals(extResponse1.artifactId(), artifactAndUser.artifactId());
        assertEquals(extResponse1.userId(), artifactAndUser.userId());
        assertTrue(extResponse1.answer());

        // setArtifactAddedToUserFeed
        final HttpResponse response3 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.GET("http://localhost:8082/artifactState/setArtifactAddedToUserFeed?" + michael3Params))
                        .toCompletableFuture()
                        .get(requestTimeout.getSeconds(), SECONDS);
        final ArtifactStatePocAPI.CommandResponse commandResponse2 = Jackson.unmarshaller(ArtifactStatePocAPI.CommandResponse.class)
                .unmarshal(response3.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);

        assertTrue(commandResponse2.success());

        // isArtifactInUserFeed
        final HttpResponse response4 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.GET("http://localhost:8082/artifactState/isArtifactInUserFeed?" + michael3Params))
                        .toCompletableFuture()
                        .get(requestTimeout.getSeconds(), SECONDS);

        final ArtifactStatePocAPI.ExtResponse extResponse2 = Jackson.unmarshaller(ArtifactStatePocAPI.ExtResponse.class)
                .unmarshal(response4.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertSame(extResponse2.artifactId(), artifactAndUser.artifactId());
        assertEquals(extResponse2.userId(), artifactAndUser.userId());
        assertTrue(extResponse2.answer());

        // setArtifactRemovedFromUserFeed
        final HttpResponse response5 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.GET("http://localhost:8082/artifactState/setArtifactRemovedFromUserFeed?" + michael3Params))
                        .toCompletableFuture()
                        .get(requestTimeout.getSeconds(), SECONDS);
        final ArtifactStatePocAPI.CommandResponse commandResponse3 = Jackson.unmarshaller(ArtifactStatePocAPI.CommandResponse.class)
                .unmarshal(response5.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);

        assertTrue(commandResponse3.success());

        // getAllStates
        final HttpResponse response6 =
                Http.get(endpointNode3.system)
                        .singleRequest(HttpRequest.GET("http://localhost:8082/artifactState/getAllStates?" + michael3Params))
                        .toCompletableFuture()
                        .get(requestTimeout.getSeconds(), SECONDS);

        final ArtifactStatePocAPI.AllStatesResponse allStatesResponse = Jackson.unmarshaller(ArtifactStatePocAPI.AllStatesResponse.class)
                .unmarshal(response6.entity(), endpointNode3.system)
                .toCompletableFuture().get(requestTimeout.getSeconds(), SECONDS);
        assertSame(allStatesResponse.artifactId(), artifactAndUser.artifactId());
        assertEquals(allStatesResponse.userId(), artifactAndUser.userId());
        assertTrue(allStatesResponse.artifactRead());
        assertFalse(allStatesResponse.artifactInUserFeed());

        logger.info("testAllViaHttpGET completed...");
    }
}
