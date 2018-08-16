package io.cresco.library.messaging;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MsgEventParamsAdapter extends XmlAdapter<MsgEventParamsAdapter.AdaptedMap, Map<String, String>> {

    static class AdaptedMap {
        final List<Entry> entry = new ArrayList<Entry>();
    }

    private static class Entry {
        String key;
        String value;
    }

    @Override
    public Map<String, String> unmarshal(AdaptedMap adaptedMap) {
        Map<String, String> map = new HashMap<String, String>();
        for(Entry entry : adaptedMap.entry) {
            map.put(entry.key, entry.value);
        }
        return map;
    }

    @Override
    public AdaptedMap marshal(Map<String, String> map) {
        AdaptedMap adaptedMap = new AdaptedMap();
        for(Map.Entry<String, String> mapEntry : map.entrySet()) {
            Entry entry = new Entry();
            entry.key = mapEntry.getKey();
            entry.value = mapEntry.getValue();
            adaptedMap.entry.add(entry);
        }
        return adaptedMap;
    }
}