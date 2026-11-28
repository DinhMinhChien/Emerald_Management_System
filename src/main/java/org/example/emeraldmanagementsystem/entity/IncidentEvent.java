package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "incident_events")
public class IncidentEvent extends BaseEntity {
    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id ;

    @Column(name = "incident_id")
    private String incidentId ;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private IncidentStatus status ;

    @Column(name = "assignee_name")
    private String assigneeName ;

    @Column(name = "note")
    private String note ;
}
