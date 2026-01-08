package com.workflow_worker.demo.entity;

import jakarta.persistence.*;
import lombok.Data;


import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "workflows")
public class Workflow {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "jsonb")
    private String spec;

    private boolean active = false;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}