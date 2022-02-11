package com.lightbend.artifactstate.endpoint;

import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ArtifactStatePocAPI {

    public static class ArtifactAndUser {
        Long artifactId;
        String userId;

        @JsonCreator
        public ArtifactAndUser(@JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId) {
            this.artifactId = artifactId;
            this.userId = userId;
        }

        public Long getArtifactId() {
            return artifactId;
        }

        public String getUserId() {
            return userId;
        }
    }

    public interface ExtResponses {}

    public static class ExtResponse implements ExtResponses {
        Long artifactId;
        String userId;
        Optional<Boolean> answer = Optional.empty();
        Optional<String> failureMsg = Optional.empty();

/*        public ExtResponse(Long artifactId, String userId) {
            this.artifactId = artifactId;
            this.userId = userId;
        }*/

        @JsonCreator
        public ExtResponse(@JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId, @JsonProperty("answer") Boolean answer) {
            this.artifactId = artifactId;
            this.userId = userId;
            this.answer = Optional.of(answer);
        }

        @JsonCreator
        public ExtResponse(@JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId, @JsonProperty("failureMsg") String failureMsg) {
            this.artifactId = artifactId;
            this.userId = userId;
            this.answer = Optional.empty();
            this.failureMsg = Optional.of(failureMsg);
        }

        public Long getArtifactId() {
            return artifactId;
        }

        public String getUserId() {
            return userId;
        }

        public Optional<Boolean> getAnswer() {
            return answer;
        }

        public Optional<String> getFailureMsg() {
            return failureMsg;
        }
    }

    public static class AllStatesResponse implements ExtResponses {
        Long artifactId;
        String userId;
        Boolean artifactRead = false;
        Boolean artifactInUserFeed = false;
        Optional<String> failureMsg = Optional.empty();

        @JsonCreator
        public AllStatesResponse(@JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId, @JsonProperty("artifactRead") Boolean artifactRead, @JsonProperty("artifactInUserFeed") Boolean artifactInUserFeed) {
            this.artifactId = artifactId;
            this.userId = userId;
            this.artifactRead = artifactRead;
            this.artifactInUserFeed = artifactInUserFeed;
        }

        @JsonCreator
        public AllStatesResponse(@JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId, @JsonProperty("failureMsg") String failureMsg) {
            this.artifactId = artifactId;
            this.userId = userId;
            this.failureMsg = Optional.of(failureMsg);
        }

        public Long getArtifactId() {
            return artifactId;
        }

        public String getUserId() {
            return userId;
        }

        public Boolean getArtifactRead() {
            return artifactRead;
        }

        public Boolean getArtifactInUserFeed() {
            return artifactInUserFeed;
        }

        public Optional<String> getFailureMsg() {
            return failureMsg;
        }
    }

    public static class CommandResponse implements ExtResponses {
        Boolean success;

        @JsonCreator
        public CommandResponse(@JsonProperty("success") Boolean success) {
            this.success = success;
        }

        public Boolean getSuccess() {
            return success;
        }
    }
}
