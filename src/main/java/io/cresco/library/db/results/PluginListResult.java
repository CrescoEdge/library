package io.cresco.library.db.results;

import java.util.List;

public class PluginListResult {
    private List<ListEntry> plugins;

    public PluginListResult(List<ListEntry> toAdd){
        this.plugins = toAdd;
    }

    public List<ListEntry>getPlugins(){
        return this.plugins;
    }

    public static class ListEntry{
        private String name;
        private String region;
        private String agent;

        public ListEntry(String name, String region, String agent){
            this.name = name;
            this.region = region;
            this.agent = agent;
        }
    }
}
