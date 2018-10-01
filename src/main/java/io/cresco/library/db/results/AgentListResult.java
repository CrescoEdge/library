package io.cresco.library.db.results;

import java.util.List;

public class AgentListResult {
    ListEntry[] agents;
    public AgentListResult(ListEntry[]  toAdd){
        this.agents = toAdd;
    }

    public ListEntry[]  getAgents() {
        return agents;
    }

    public static class ListEntry{
        private String name;
        private String region;
        private long plugins;
        private String location;
        private String platform;
        private String environment;

        public ListEntry(String name, String region, long plugins, String location, String platform,
                         String environment){
            this.name = name;
            this.region = region;
            this.plugins = plugins;
            this.location = location;
            this.platform = platform;
            this.environment = environment;
        }
    }
}
