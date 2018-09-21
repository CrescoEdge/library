package io.cresco.library.db;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name="region"
        ,uniqueConstraints = {@UniqueConstraint(columnNames={"region_name"})})
public class RegionRecord {
    @Id
    @GeneratedValue
    private Long id;

    @Column(name="region_name")
    private String name;

    protected RegionRecord(){}

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
