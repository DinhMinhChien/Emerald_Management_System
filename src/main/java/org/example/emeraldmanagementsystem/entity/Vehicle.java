package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.enums.VehicleStatus;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id;

    @Column(name = "apartment_id")
    private String apartmentId;

    @Column(name = "user_resident_id")
    private String userResidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private VehicleType type;

    @Column(name = "plate_number")
    private String plateNumber;

    @Column(name = "card_number")
    private String cardNumber;

    @Column(name = "issued_at")
    private LocalDate issuedAt = LocalDate.now();

    @Column(name = "revoked_at")
    private LocalDate revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private VehicleStatus status = VehicleStatus.ACTIVE ;

    @Column(name = "active_plate_number", insertable = false, updatable = false)
    private String activePlateNumber;

    @Column(name = "active_card_number", insertable = false, updatable = false)
    private String activeCardNumber;

}