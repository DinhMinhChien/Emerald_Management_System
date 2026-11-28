package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.enums.IncidentCancelReason;
import org.example.emeraldmanagementsystem.common.enums.IncidentChannel;
import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "incidents")
public class Incident extends BaseEntity{
    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id ;

    @Column(name = "apartment_id")
    private String apartmentId ;

    @Column(name = "reported_by")
    private String reportedBy ;

    @Column(name = "reporter_name")
    private String reporterName ;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel")
    private IncidentChannel channel ;

    @Column(name = "title")
    private String title ;

    @Column(name = "description")
    private String description ;


    @Column(name = "area")
    private String area ;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private IncidentStatus status ;

    @Column(name = "assignee_name")
    private String assigneeName ;

    @Column(name = "closed_at")
    private LocalDateTime closedAt ;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt ;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason")
    private IncidentCancelReason cancelReason ;

}
