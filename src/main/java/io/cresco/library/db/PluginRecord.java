package io.cresco.library.db;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;


@Entity
@Table(name="plugin"
        ,uniqueConstraints = {@UniqueConstraint(columnNames={"plugin_name"})})
public class PluginRecord {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name="agent_id")
    private AgentRecord agent;

    @ManyToOne
    @JoinColumn(name="region_id")
    private RegionRecord region;

    @Column
    private String name;

    @Column
    private String assignedPluginId;

    protected PluginRecord(){}

    public Long getId() {
        return id;
    }

    public AgentRecord getAgent() {
        return agent;
    }

    public void setAgent(AgentRecord agent) {
        this.agent = agent;
    }

    public RegionRecord getRegion() {
        return region;
    }

    public void setRegion(RegionRecord region) {
        this.region = region;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
