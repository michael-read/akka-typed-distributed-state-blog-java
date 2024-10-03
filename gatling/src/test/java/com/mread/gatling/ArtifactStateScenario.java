package com.mread.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.gatling.core.body.StringBody;
import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ArtifactStateScenario extends Simulation {

    private Config config = ConfigFactory.load();

    private String baseUrl = config.getString("loadtest.baseUrl");

    private FeederBuilder.Batchable<String> namesFeeder = csv("lastnames.csv").random();

    private Random random = new Random();

    private Iterator<Map<String, Object>> artifactIds =
            Stream.generate((Supplier<Map<String, Object>>) () -> {
                        Integer artifactId = random.nextInt(500);
                        return Map.of("artifactId", artifactId);
                    }
            ).iterator();

    Body.WithString artifactAndUser = StringBody("{ \"artifactId\": #{artifactId}, \"userId\": \"#{name}\" }");

    HttpProtocolBuilder httpProtocol =
            http.baseUrl(String.format("%s/artifactState", baseUrl))
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json");

    ScenarioBuilder scn = scenario("ArtifactStateScenario")
            .feed(namesFeeder)
            .feed(artifactIds)

            .exec(
                    http("set_artifact_read")
                            .post("/setArtifactReadByUser")
                            .body(artifactAndUser).asJson()
                            .check(status().is(200))
            )

            .exec(
                    http("is_artifact_read")
                            .post("/isArtifactReadByUser")
                            .body(artifactAndUser).asJson()
                            .check(status().is(200))
            )

            .exec(
                    http("set_artifact_in_feed")
                            .post("/setArtifactAddedToUserFeed")
                            .body(artifactAndUser).asJson()
                            .check(status().is(200))
            )

            .exec(
                    http("is_artifact_in_user_feed")
                            .post("/isArtifactInUserFeed")
                            .body(artifactAndUser).asJson()
                            .check(status().is(200))
            )

            .exec(
                    http("set_artifact_removed_from_feed")
                            .post("/setArtifactRemovedFromUserFeed")
                            .body(artifactAndUser).asJson()
                            .check(status().is(200))
            )

            .exec(
                    http("get_all_states")
                            .post("/getAllStates")
                            .body(artifactAndUser).asJson()
                            .check(status().is(200))
            );
    {
        setUp(
//                myFirstScenario.injectOpen(constantUsersPerSec(2).during(60))
//    scn.injectOpen(atOnceUsers(1)
//    scn.injectOpen(rampUsers(100).during(Duration.ofMinutes(3)))
    scn.injectOpen(rampUsers(1000).during(Duration.ofMinutes(5)))
// simulation set up -> -> https://docs.gatling.io/reference/script/core/injection/#open-model
/*
            scn.injectOpen(
                    nothingFor(Duration.ofSeconds(4)), // 1
                    atOnceUsers(10), // 2
                    rampUsers(10).during(Duration.ofSeconds(5)), // 3
                    constantUsersPerSec(20).during(Duration.ofSeconds(15)), // 4
                    constantUsersPerSec(20).during(Duration.ofSeconds(15)).randomized(), // 5
                    rampUsersPerSec(10).to(20).during(Duration.ofMinutes(10)), // 6
                    rampUsersPerSec(10).to(20).during(Duration.ofMinutes(10)).randomized(), // 7
                    stressPeakUsers(1000).during(Duration.ofSeconds(20)) // 8
            )
*/
            .protocols(httpProtocol)
        );
    }
}
