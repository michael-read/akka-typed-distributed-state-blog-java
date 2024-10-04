package com.lightbend.artifactstate.endpoint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

// these are just for the JSON formats/external protocol/api
public class ArtifactStatePocAPI {

    public record ArtifactAndUser(@JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId) {}

    public interface ExtResponses {}

    public record ExtResponse(@JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId, @JsonProperty("answer") Boolean answer, @JsonProperty("failureMsg") String failureMsg) {
        public ExtResponse(Long artifactId, String userId, Boolean answer) {
            this(artifactId, userId, answer, "");
        }
        public ExtResponse(Long artifactId, String userId, String failureMsg) {
            this(artifactId, userId, false, failureMsg);
        }
    }

    public record AllStatesResponse(Long artifactId, String userId, Boolean artifactRead, Boolean artifactInUserFeed, @JsonIgnore String failureMsg) implements ExtResponses {
        public AllStatesResponse(Long artifactId, String userId, String failureMsg) {
            this(artifactId, userId, false, false, failureMsg);
        }
        public AllStatesResponse(@JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId, @JsonProperty("artifactRead") Boolean artifactRead, @JsonProperty("artifactInUserFeed") Boolean artifactInUserFeed) {
            this(artifactId, userId, artifactRead, artifactInUserFeed, "");
        }
    }

    public record CommandResponse(@JsonProperty("success") Boolean success) {}
}
