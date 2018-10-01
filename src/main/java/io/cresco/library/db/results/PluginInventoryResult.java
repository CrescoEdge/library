package io.cresco.library.db.results;

import io.cresco.library.db.PluginRecord;

import java.util.List;

public class PluginInventoryResult {

    private InventoryEntry[] plugins;

    public PluginInventoryResult(InventoryEntry[] toAdd){
        plugins = toAdd;
    }

    public InventoryEntry[] getPlugins() {
        return plugins;
    }

    public static class InventoryEntry{
        private String jarFileName;
        private String pluginName;
        private String md5;
        private String version;

        public InventoryEntry(String pluginName, String jarFileName, String md5, String version) {
            this.jarFileName = jarFileName;
            this.pluginName = pluginName;
            this.md5 = md5;
            this.version = version;
        }

        public String getJarFileName() {
            return jarFileName;
        }

        public String getPluginName() {
            return pluginName;
        }

        public String getMd5() {
            return md5;
        }

        public String getVersion() {
            return version;
        }
    }

}
