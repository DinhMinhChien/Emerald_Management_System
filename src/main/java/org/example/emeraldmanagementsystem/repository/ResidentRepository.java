package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ResidentRepository extends JpaRepository<Resident, String> {
    Optional<Resident> findByIdAndDeletedFalse(String id);
    Optional<Resident> findByNationalIdAndDeletedFalse(String nationalId);

    @Query(
            value = """
                    select distinct resident.id as id,
                           resident.fullName as fullName,
                           resident.phone as phone,
                           apartment.id as apartmentId,
                           apartment.code as apartmentCode,
                           tower.code as towerCode,
                           tower.name as towerName,
                           residency.role as role,
                           residency.relation as relation
                    from Resident resident
                    left join Residency residency
                      on residency.residentId = resident.id
                     and residency.deleted = false
                     and residency.endDate is null
                    left join Apartment apartment
                      on apartment.id = residency.apartmentId
                     and apartment.deleted = false
                    left join Tower tower
                      on tower.id = apartment.towerId
                     and tower.deleted = false
                    where resident.deleted = false
                      and (:residing = false or residency.id is not null)
                      and (:towerId is null or tower.id = :towerId)
                      and (:query is null
                           or lower(resident.fullName) like :query
                           or lower(resident.phone) like :query)
                    """,
            countQuery = """
                    select count(distinct resident.id)
                    from Resident resident
                    left join Residency residency
                      on residency.residentId = resident.id
                     and residency.deleted = false
                     and residency.endDate is null
                    left join Apartment apartment
                      on apartment.id = residency.apartmentId
                     and apartment.deleted = false
                    left join Tower tower
                      on tower.id = apartment.towerId
                     and tower.deleted = false
                    where resident.deleted = false
                      and (:residing = false or residency.id is not null)
                      and (:towerId is null or tower.id = :towerId)
                      and (:query is null
                           or lower(resident.fullName) like :query
                           or lower(resident.phone) like :query)
                    """
    )
    Page<ResidentListProjection> searchResidents(
            @Param("query") String query,
            @Param("towerId") String towerId,
            @Param("residing") boolean residing,
            Pageable pageable
    );

    interface ResidentListProjection {
        String getId();
        String getFullName();
        String getPhone();
        String getApartmentId();
        String getApartmentCode();
        String getTowerCode();
        String getTowerName();
        ResidencyRole getRole();
        ResidencyRelation getRelation();
    }
}
