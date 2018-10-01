package io.cresco.library.db.results;

import java.util.ArrayList;
import java.util.List;

public class RepoInfoResult {
    private final ListEntry[] repoInfo;

    public RepoInfoResult(ListEntry[] repoInfo) {
        this.repoInfo = repoInfo;
    }

    public ListEntry[] getRepoInfo() {
        return repoInfo;
    }

    public RepoInfoResult(){
        repoInfo = new ListEntry[]{};
    }

    public static class ListEntry {
        final String regionName;
        final String agentName;
        final String pluginId;

        public ListEntry(String regionName, String agentName, String pluginId) {
            this.regionName = regionName;
            this.agentName = agentName;
            this.pluginId = pluginId;
        }

        public String getRegionName() {
            return regionName;
        }

        public String getAgentName() {
            return agentName;
        }

        public String getPluginId() {
            return pluginId;
        }
    }

}
