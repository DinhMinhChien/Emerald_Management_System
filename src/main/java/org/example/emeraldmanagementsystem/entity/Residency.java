package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.enums.ResidencyEndReason;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;

@Entity
@Table(name = "residencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Residency extends BaseEntity{
    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id;

    @Column(name = "apartment_id")
    private String apartmentId;

    @Column(name = "resident_id")
    private String residentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ResidencyRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation")
    private ResidencyRelation relation;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason")
    private ResidencyEndReason endReason;

    @Column(name = "active_head_apartment_id", length = 36, insertable = false, updatable = false)
    private String activeHeadApartmentId;

    @Column(name = "active_resident_id", length = 36, insertable = false, updatable = false)
    private String activeResidentId;

}