package io.cresco.library.db;

import io.cresco.library.agent.AgentState;
import io.cresco.library.agent.ControllerState;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name="agent"
        ,uniqueConstraints = {@UniqueConstraint(columnNames={"name"})})
public class AgentRecord {
    public enum AgentStatus{
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED
    }

    @Id
    @GeneratedValue
    private long id;

    @Column
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

    @Column
    private ControllerState.Mode agentMode;

    @Column
    private LocalDateTime lastUpdated;

    @Column
    private String msgInUrl;


    protected AgentRecord() {}

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RegionRecord getRegion() {
        return region;
    }

    public String getLocation() {
        return location;
    }

    public String getPlatform() {
        return platform;
    }

    public String getEnvironment() {
        return environment;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public String getMsgInUrl() {
        return msgInUrl;
    }


    public void setName(String name) {
        this.name = name;
    }

    public void setRegion(RegionRecord region) {
        this.region = region;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void setMsgInUrl(String msgInUrl) {
        this.msgInUrl = msgInUrl;
    }
}
