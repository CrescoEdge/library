package io.cresco.library.db;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


@Entity
@Table(name="plugin"
        ,uniqueConstraints = {@UniqueConstraint(columnNames={"plugin_name"})})
public class PluginRecord {
    public enum PluginStatus{
        STOPPED,
        LOADING,
        LOADED,
        RUNNING
    }
    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    @JoinColumn(name="agent_id")
    private AgentRecord agent;

    @ManyToOne
    @JoinColumn(name="region_id")
    private RegionRecord region;

    @Column
    private String name;

    @Column
    private String assignedToPluginSlot;

    @Column
    private PluginStatus status;

    @Column
    private String md5;

    @Column
    private String jarFileName;

    @Column
    private String version;

    @Column
    private LocalDateTime lastUpdated;

    @Column
    private String compressedPluginConfig;

    public PluginRecord() {}

    public Long getId() {
        return id;
    }

    public AgentRecord getAgent() {
        return agent;
    }

    public RegionRecord getRegion() {
        return region;
    }

    public String getName() {
        return name;
    }

    public String getAssignedToPluginSlot() {
        return assignedToPluginSlot;
    }

    public PluginStatus getStatus() {
        return status;
    }

    public String getMd5() {
        return md5;
    }

    public String getJarFileName() {
        return jarFileName;
    }

    public String getVersion() {
        return version;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public String getCompressedPluginConfig() {
        return compressedPluginConfig;
    }

    public void setAgent(AgentRecord agent) {
        this.agent = agent;
    }

    public void setRegion(RegionRecord region) {
        this.region = region;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAssignedToPluginSlot(String assignedToPluginSlot) {
        this.assignedToPluginSlot = assignedToPluginSlot;
    }

    public void setStatus(PluginStatus status) {
        this.status = status;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public void setJarFileName(String jarFileName) {
        this.jarFileName = jarFileName;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void setCompressedPluginConfig(String compressedPluginConfig) {
        this.compressedPluginConfig = compressedPluginConfig;
    }
}
