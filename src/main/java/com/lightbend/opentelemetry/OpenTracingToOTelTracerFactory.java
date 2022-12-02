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
    private final boolean enableGlobalTracer;

    public OpenTracingToOTelTracerFactory(String configPath, Config config, LoggingProvider logging) {
        this.log = logging.get(this.getClass());
        this.config = Configuration.getConfig(config, configPath);
        this.enableGlobalTracer = this.config.getBoolean("opentracing.opentelemetryshim.enableGlobalTracer");
    }

    public Tracer create() {
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
