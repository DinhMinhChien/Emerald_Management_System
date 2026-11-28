package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;

@Entity
@Table(name = "vehicle_quotas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VehicleQuota extends BaseEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, updatable = false)
    private VehicleType vehicleType;

    @Column(name = "max_per_apartment", nullable = false)
    private int maxPerApartment;

}