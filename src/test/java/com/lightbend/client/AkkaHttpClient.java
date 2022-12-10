package com.lightbend.client;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.stream.SystemMaterializer;

import java.util.concurrent.CompletionStage;

public class AkkaHttpClient {
    private final ActorSystem sys;
    private final AkkaHttpClientSettings settings;
    public AkkaHttpClient(AkkaHttpClientSettings settings, ActorSystem sys) {
        this.settings = settings;
        this.sys = sys;
    }
    public static AkkaHttpClient create(AkkaHttpClientSettings settings, ActorSystem sys) {
        return new AkkaHttpClient(settings, sys);
    }

    public CompletionStage<HttpResponse> get(String api, String json) {
        return Http.get(sys)
                .singleRequest(HttpRequest.create(settings.host() + ""));
    }

    public CompletionStage<HttpResponse> post(String api, String json) {
        return Http.get(sys)
                .singleRequest(HttpRequest.POST(settings.host())
                .withEntity(HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, "data")));
    }

}
