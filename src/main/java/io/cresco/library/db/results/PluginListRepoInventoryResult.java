package db.jpa.queryresults;

import java.util.List;

public class PluginListRepoInventoryResult {
    List<String> repoList;

    public PluginListRepoInventoryResult(List<String>toAdd){
        this.repoList = toAdd;
    }
}
