package com.teacher.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "majors")
public class Major {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "parent_code", length = 16)
    private String parentCode;

    @Column(nullable = false)
    private Integer level;

    // getter/setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
}
