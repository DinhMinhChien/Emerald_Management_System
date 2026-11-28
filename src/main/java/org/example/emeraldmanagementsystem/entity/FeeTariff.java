package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fee_tariffs")
public class FeeTariff extends BaseEntity{
    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id ;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type")
    private FeeType feeType ;

    @Column(name = "tier_name")
    private String tierName ;

    @Column(name = "min_area_m2")
    private BigDecimal minAreaM2 ;

    @Column(name = "max_area_m2")
    private BigDecimal maxAreaM2 ;

    @Column(name = "amount")
    private Long amount ;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom ;

    @Column(name = "effective_to")
    private LocalDate effectiveTo ;
}
