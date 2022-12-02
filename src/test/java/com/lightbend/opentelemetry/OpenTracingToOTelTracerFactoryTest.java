package com.lightbend.opentelemetry;

import akka.NotUsed;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorSystem;
import cinnamon.core.akka.AkkaLoggingProvider;
import com.lightbend.artifactstate.MultiNodeIntegrationTest;
import com.lightbend.cinnamon.logging.LoggingProvider;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.opentracing.Tracer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class OpenTracingToOTelTracerFactoryTest {
    private static final Logger log = LoggerFactory.getLogger(OpenTracingToOTelTracerFactoryTest.class);

    private static final Config config = ConfigFactory.load("opentracing-test.conf");

    private static LoggingProvider loggingProvider;

    private static OpenTracingToOTelTracerFactory factory;

    private static MyTestFixture testFixture;

    private static class MyTestFixture {
        private final ActorTestKit testKit;
        private final ActorSystem<?> system;

        public MyTestFixture() {
            testKit = ActorTestKit.create("TestKit");
            system = testKit.system();
        }
    }

    @BeforeClass
    public static void setup() {
        log.info("starting test");
        testFixture = new MyTestFixture();
        loggingProvider = new AkkaLoggingProvider(testFixture.system.classicSystem());
        factory = new OpenTracingToOTelTracerFactory(config, loggingProvider);
    }

    @Test
    public void testFactor() throws Exception {
        Tracer tracer = factory.create();
        assertTrue(tracer != null);
    }

    @AfterClass
    public static void tearDown() {
        log.info("stopping test");
        testFixture.testKit.shutdownTestKit();
    }

}
