package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.ApartmentStatus;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "apartments")
public class Apartment extends BaseEntity{
    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id ;

    @Column(name = "tower_id")
    private String towerId ;

    @Column(name = "code")
    private String code ;

    @Column(name = "floor")
    private int floor ;

    @Column(name = "area_m2")
    private BigDecimal areaM2 ;

    @Column(name = "bedrooms")
    private int bedrooms ;

    @Column(name = "bathrooms")
    private int bathrooms ;

    @Enumerated(EnumType.STRING)
    @Column(name = "status" )
    private ApartmentStatus status = ApartmentStatus.VACANT ;

}
