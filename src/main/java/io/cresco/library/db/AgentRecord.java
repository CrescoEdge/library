package io.cresco.library.db;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name="agent"
        ,uniqueConstraints = {@UniqueConstraint(columnNames={"agent_name"})})
public class AgentRecord {

    @Id
    @GeneratedValue
    private long id;

    @Column(name="agent_name")
    private String name;


    @ManyToOne
    @JoinColumn(name="region_id")
    private RegionRecord region;

    @ElementCollection
    private Map<String,String> recordParams = new HashMap<>();

    protected AgentRecord() {
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RegionRecord getRegion() {
        return region;
    }

    public void setRegion(RegionRecord region) {
        this.region = region;
    }

    public Map<String, String> getRecordParams() {
        return recordParams;
    }

    public void setRecordParams(Map<String, String> recordParams) {
        this.recordParams = recordParams;
    }
}
