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

    @Column
    private String location;

    @Column
    private String platform;

    @Column
    private String environment;

    protected AgentRecord() {}

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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}
