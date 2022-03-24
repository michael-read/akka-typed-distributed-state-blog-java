package com.lightbend.artifactstate.endpoint;

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
        Boolean answer = false; // Opational<Boolean> doesn't work properly with JSON marshalling
        String failureMsg = "";

        @JsonCreator
        public ExtResponse(@JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId, @JsonProperty("answer") Boolean answer, @JsonProperty("failureMsg") String failureMsg) {
            this.artifactId = artifactId;
            this.userId = userId;
            this.answer = answer;
            this.failureMsg = failureMsg;
        }

        public ExtResponse(Long artifactId, String userId, Boolean answer) {
            this.artifactId = artifactId;
            this.userId = userId;
            this.answer = answer;
        }

        public ExtResponse(Long artifactId, String userId, String failureMsg) {
            this.artifactId = artifactId;
            this.userId = userId;
            this.failureMsg = failureMsg;
        }

        public Long getArtifactId() {
            return artifactId;
        }

        public String getUserId() {
            return userId;
        }

        public Boolean getAnswer() {
            return answer;
        }

        public String getFailureMsg() {
            return failureMsg;
        }
    }

    public static class AllStatesResponse implements ExtResponses {
        Long artifactId;
        String userId;
        Boolean artifactRead = false;
        Boolean artifactInUserFeed = false;
        String failureMsg = "";

        @JsonCreator
        public AllStatesResponse(@JsonProperty("artifactId") Long artifactId, @JsonProperty("userId") String userId, @JsonProperty("artifactRead") Boolean artifactRead, @JsonProperty("artifactInUserFeed") Boolean artifactInUserFeed) {
            this.artifactId = artifactId;
            this.userId = userId;
            this.artifactRead = artifactRead;
            this.artifactInUserFeed = artifactInUserFeed;
        }

        public AllStatesResponse(Long artifactId, String userId, String failureMsg) {
            this.artifactId = artifactId;
            this.userId = userId;
            this.failureMsg = failureMsg;
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

        public String getFailureMsg() {
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
