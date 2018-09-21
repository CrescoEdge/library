package io.cresco.library.db.results;

import java.util.List;

public class PluginListRepoInventoryResult {
    List<String> repoList;

    public PluginListRepoInventoryResult(List<String>toAdd){
        this.repoList = toAdd;
    }
}
