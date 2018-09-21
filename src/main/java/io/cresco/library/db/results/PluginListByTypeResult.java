package db.jpa.queryresults;

import java.util.List;
import java.util.Map;

public class PluginListByTypeResult {
    private List<ListEntry> plugins;

    public PluginListByTypeResult(List<ListEntry> toAdd){
        this.plugins = toAdd;
    }

    public List<ListEntry>getPlugins(){
        return this.plugins;
    }

    public static class ListEntry{
        private String agentcontroller;
        private String region;
        private String agent;
        private Map<String,String> nodeParams;

        public ListEntry(String agentcontroller,
                         String region,
                         String agent,
                         Map<String,String> nodeParams){
            this.agentcontroller = agentcontroller;
            this.region = region;
            this.agent = agent;
            this.nodeParams = nodeParams;
        }

        public String getAgentcontroller() {
            return agentcontroller;
        }

        public String getRegion() {
            return region;
        }

        public String getAgent() {
            return agent;
        }

        public Map<String, String> getNodeParams() {
            return nodeParams;
        }
    }
}
