package io.cresco.library.db.results;

public class RepoListResult {
    RepoInfoResult server;
    PluginInventoryResult plugins;

    public RepoListResult(RepoInfoResult server,
                          PluginInventoryResult plugins) {
        this.server = server;
        this.plugins = plugins;
    }

    public RepoListResult(){}//no-arg for deserialization support

    public RepoInfoResult getServer() {
        return server;
    }

    public PluginInventoryResult getPlugins() {
        return plugins;
    }
}
