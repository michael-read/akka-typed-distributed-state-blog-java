package com.lightbend.opentelemetry;

import com.typesafe.config.Config;

import cinnamon.config.Configuration;
import com.lightbend.cinnamon.logging.Logger;
import com.lightbend.cinnamon.logging.LoggingProvider;
import com.lightbend.cinnamon.opentracing.TracerFactory;

import io.opentelemetry.opentracingshim.OpenTracingShim;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;


public class OpenTracingToOTelTracerFactory implements TracerFactory {

    private final Config config;
    private final Logger log;
    private final boolean openTelemetryShimEnabled;
    private final boolean enableGlobalTracer;

    public OpenTracingToOTelTracerFactory(String configPath, Config config, LoggingProvider logging) {
        this.config = Configuration.getConfig(config, configPath);
        this.log = logging.get(this.getClass());
        this.openTelemetryShimEnabled = this.config.getBoolean("app.opentracing.opentelemetryshim.enabled");
        this.enableGlobalTracer = this.config.getBoolean("app.opentracing.opentelemetryshim.enableGlobalTracer");
    }

    public OpenTracingToOTelTracerFactory(Config config, LoggingProvider logging) {
        this.config = config;
        this.log = logging.get(this.getClass());
        this.openTelemetryShimEnabled = this.config.getBoolean("app.opentracing.opentelemetryshim.enabled");
        this.enableGlobalTracer = this.config.getBoolean("app.opentracing.opentelemetryshim.enableGlobalTracer");
    }

    public Tracer create() {
        if (!openTelemetryShimEnabled) {
            log.info("returning io.opentracing.noop.NoopTracerFactory.create()");
            return io.opentracing.noop.NoopTracerFactory.create();
        }
        try {
            Tracer tracer = OpenTracingShim.createTracerShim();
            if (enableGlobalTracer) {
                GlobalTracer.registerIfAbsent(tracer);
            }
            log.info("created tracer shim");
            return tracer;
        }
        catch (Exception e) {
            log.error("couldn't create tracer shim", e);
            throw new RuntimeException(e);
        }
    }

}
