package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.example.emeraldmanagementsystem.entity.Residency;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ApartmentRepository extends JpaRepository<Apartment, String> {
    Optional<Apartment> findByIdAndDeletedFalse(String id);
    List<Apartment> findAllByDeletedFalseOrderByCodeAsc();
    boolean existsByCode(String code);
    long countByDeletedFalse();
    long countByDeletedFalseAndStatus(ApartmentStatus status);

    @Query(
            value = """
                    select distinct apartment
                    from Apartment apartment
                    left join Residency headResidency
                      on headResidency.apartmentId = apartment.id
                     and headResidency.endDate is null
                     and headResidency.role = :headRole
                     and headResidency.deleted = false
                    left join Resident head
                      on head.id = headResidency.residentId
                     and head.deleted = false
                    where apartment.deleted = false
                      and (:towerId is null or apartment.towerId = :towerId)
                      and (:status is null or apartment.status = :status)
                      and (:hasHead is null
                           or (:hasHead = true and head.id is not null)
                           or (:hasHead = false and head.id is null))
                      and (:query is null
                           or lower(apartment.code) like :query
                           or lower(head.fullName) like :query)
                    """,
            countQuery = """
                    select count(distinct apartment.id)
                    from Apartment apartment
                    left join Residency headResidency
                      on headResidency.apartmentId = apartment.id
                     and headResidency.endDate is null
                     and headResidency.role = :headRole
                     and headResidency.deleted = false
                    left join Resident head
                      on head.id = headResidency.residentId
                     and head.deleted = false
                    where apartment.deleted = false
                      and (:towerId is null or apartment.towerId = :towerId)
                      and (:status is null or apartment.status = :status)
                      and (:hasHead is null
                           or (:hasHead = true and head.id is not null)
                           or (:hasHead = false and head.id is null))
                      and (:query is null
                           or lower(apartment.code) like :query
                           or lower(head.fullName) like :query)
                    """
    )
    Page<Apartment> searchApartments(
            @Param("query") String query,
            @Param("towerId") String towerId,
            @Param("status") ApartmentStatus status,
            @Param("hasHead") Boolean hasHead,
            @Param("headRole") ResidencyRole headRole,
            Pageable pageable
    );
}
