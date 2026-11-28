package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.example.emeraldmanagementsystem.entity.FeeTariff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FeeTariffRepository extends JpaRepository<FeeTariff, String> {
    @Query("""
            select tariff
            from FeeTariff tariff
            where tariff.deleted = false
              and tariff.feeType = :feeType
              and tariff.effectiveFrom <= :today
              and (tariff.effectiveTo is null or tariff.effectiveTo >= :today)
            """)
    List<FeeTariff> findCurrentByFeeType(
            @Param("feeType") FeeType feeType,
            @Param("today") LocalDate today
    );

    @Query("""
            select tariff
            from FeeTariff tariff
            where tariff.deleted = false
              and tariff.effectiveFrom <= :today
              and (tariff.effectiveTo is null or tariff.effectiveTo >= :today)
            order by tariff.feeType asc,
                     tariff.minAreaM2 asc,
                     tariff.effectiveFrom desc
            """)
    List<FeeTariff> findAllCurrent(
            @Param("today") LocalDate today
    );
}
