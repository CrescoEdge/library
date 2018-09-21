package db.jpa.queryresults;

import globalscheduler.pNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PluginListRepoSetResult {

    private Map<String, Set<pNode>> pluginRepoMap;

    public PluginListRepoSetResult(){
        pluginRepoMap = new HashMap<>();
    }

    public void add(String k, Set<pNode> v){
        pluginRepoMap.merge(k,v,(someSet,anotherSet)-> {someSet.addAll(anotherSet);return someSet;});
    }

    public Set<pNode> get(String k){
        return pluginRepoMap.get(k);
    }
}
