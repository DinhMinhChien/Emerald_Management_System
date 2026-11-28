package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.common.enums.Gender;
import org.example.emeraldmanagementsystem.common.enums.ResidencyEndReason;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.example.emeraldmanagementsystem.entity.Residency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ResidencyRepository extends JpaRepository<Residency, String> {
    Optional<Residency> findByIdAndDeletedFalse(String id);
    Optional<Residency> findByResidentIdAndEndDateIsNullAndDeletedFalse(String residentId);
    List<Residency> findByApartmentIdAndEndDateIsNullAndDeletedFalse(String apartmentId);
    boolean existsByApartmentIdAndResidentIdAndEndDateIsNullAndDeletedFalse(
            String apartmentId,
            String residentId
    );
    boolean existsByApartmentIdAndRoleAndEndDateIsNullAndDeletedFalse(String apartmentId, ResidencyRole role);

    @Query("""
            select residency.apartmentId as apartmentId,
                   resident.id as residentId,
                   resident.fullName as fullName,
                   resident.phone as phone
            from Residency residency
            join Resident resident on resident.id = residency.residentId
            where residency.deleted = false
              and residency.endDate is null
              and residency.role = org.example.emeraldmanagementsystem.common.enums.ResidencyRole.HEAD
              and resident.deleted = false
              and residency.apartmentId in :apartmentIds
            """)
    List<ActiveHeadProjection> findActiveHeadDetailsByApartmentIds(
            @Param("apartmentIds") List<String> apartmentIds
    );

    @Query("""
            select residency.id as residencyId,
                   resident.id as residentId,
                   resident.fullName as fullName,
                   resident.phone as phone,
                   resident.birthDate as birthDate,
                   resident.gender as gender,
                   residency.role as role,
                   residency.relation as relation,
                   residency.startDate as startDate,
                   residency.endDate as endDate,
                   residency.endReason as endReason
            from Residency residency
            join Resident resident on resident.id = residency.residentId
            where residency.deleted = false
              and resident.deleted = false
              and residency.apartmentId = :apartmentId
              and (:current is null or :current = false or residency.endDate is null)
            order by residency.startDate desc, residency.id desc
            """)
    List<ResidencyDetailProjection> findResidencyDetailsByApartmentId(
            @Param("apartmentId") String apartmentId,
            @Param("current") Boolean current
    );

    @Query("""
            select residency.id as residencyId,
                   apartment.id as apartmentId,
                   apartment.code as apartmentCode,
                   tower.code as towerCode,
                   tower.name as towerName,
                   residency.role as role,
                   residency.relation as relation,
                   residency.startDate as startDate
            from Residency residency
            join Apartment apartment
              on apartment.id = residency.apartmentId
             and apartment.deleted = false
            left join Tower tower
              on tower.id = apartment.towerId
             and tower.deleted = false
            where residency.deleted = false
              and residency.endDate is null
              and residency.residentId = :residentId
            """)
    Optional<ResidentCurrentResidencyProjection> findCurrentResidencyDetailsByResidentId(
            @Param("residentId") String residentId
    );

    @Query("""
            select residency.id as residencyId,
                   apartment.id as apartmentId,
                   apartment.code as apartmentCode,
                   tower.code as towerCode,
                   tower.name as towerName,
                   residency.role as role,
                   residency.relation as relation,
                   residency.startDate as startDate,
                   residency.endDate as endDate,
                   residency.endReason as endReason
            from Residency residency
            left join Apartment apartment
              on apartment.id = residency.apartmentId
             and apartment.deleted = false
            left join Tower tower
              on tower.id = apartment.towerId
             and tower.deleted = false
            where residency.deleted = false
              and residency.residentId = :residentId
            order by residency.startDate desc, residency.id desc
            """)
    List<ResidentResidencyHistoryProjection> findResidencyHistoryByResidentId(
            @Param("residentId") String residentId
    );

    @Query("""
            select resident.id as residentId,
                   resident.fullName as fullName,
                   residency.relation as relation
            from Residency residency
            join Resident resident on resident.id = residency.residentId
            where residency.deleted = false
              and residency.endDate is null
              and residency.apartmentId = :apartmentId
              and residency.residentId <> :residentId
              and resident.deleted = false
            order by residency.role asc, resident.fullName asc, resident.id asc
            """)
    List<HousemateProjection> findCurrentHousemates(
            @Param("apartmentId") String apartmentId,
            @Param("residentId") String residentId
    );

    interface ActiveHeadProjection {
        String getApartmentId();
        String getResidentId();
        String getFullName();
        String getPhone();
    }

    interface ResidencyDetailProjection {
        String getResidencyId();
        String getResidentId();
        String getFullName();
        String getPhone();
        LocalDate getBirthDate();
        Gender getGender();
        ResidencyRole getRole();
        ResidencyRelation getRelation();
        LocalDate getStartDate();
        LocalDate getEndDate();
        ResidencyEndReason getEndReason();
    }

    interface ResidentCurrentResidencyProjection {
        String getResidencyId();
        String getApartmentId();
        String getApartmentCode();
        String getTowerCode();
        String getTowerName();
        ResidencyRole getRole();
        ResidencyRelation getRelation();
        LocalDate getStartDate();
    }

    interface ResidentResidencyHistoryProjection {
        String getResidencyId();
        String getApartmentId();
        String getApartmentCode();
        String getTowerCode();
        String getTowerName();
        ResidencyRole getRole();
        ResidencyRelation getRelation();
        LocalDate getStartDate();
        LocalDate getEndDate();
        ResidencyEndReason getEndReason();
    }

    interface HousemateProjection {
        String getResidentId();
        String getFullName();
        ResidencyRelation getRelation();
    }
}
