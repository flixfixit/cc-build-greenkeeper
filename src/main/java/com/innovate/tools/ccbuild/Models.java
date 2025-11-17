package com.innovate.tools.ccbuild;

import com.fasterxml.jackson.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Models {
    public static class Build {
        @JsonProperty("code")
        private String code;

        @JsonProperty("name")
        private String name;

        @JsonProperty("status")
        private String status;

        @JsonProperty("createdAt")
        @JsonAlias({"buildStartTimestamp", "buildEndTimestamp"})
        private Instant createdAt;

        @JsonProperty("pinned")
        private boolean pinned;

        @JsonProperty("environment")
        @JsonAlias({"branch"})
        private String environment;

        private Map<String, Object> raw = new HashMap<>();

        @JsonAnySetter
        public void put(String k, Object v) {
            raw.put(k, v);
        }

        @JsonAnyGetter
        public Map<String, Object> raw() {
            return raw;
        }

        public String code() {
            return code;
        }

        public String name() {
            return name;
        }

        public String status() {
            return status;
        }

        public Instant createdAt() {
            return createdAt;
        }

        public boolean pinned() {
            return pinned;
        }

        public String environment() {
            return environment;
        }

        @Override
        public String toString() {
            return code + "\t" + name + "\t" + status + "\t" + createdAt + "\t" + pinned + "\t" + environment;
        }
    }
}
