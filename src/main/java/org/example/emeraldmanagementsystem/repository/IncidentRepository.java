package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;
import org.example.emeraldmanagementsystem.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, String> {
    long countByDeletedFalseAndStatus(IncidentStatus status);

    List<Incident> findTop3ByDeletedFalseOrderByCreatedAtDesc();

    @Query("""
            select incident
            from Incident incident
            where incident.deleted = false
              and incident.apartmentId = :apartmentId
              and incident.status in
                  (org.example.emeraldmanagementsystem.common.enums.IncidentStatus.NEW,
                   org.example.emeraldmanagementsystem.common.enums.IncidentStatus.ACKNOWLEDGED,
                   org.example.emeraldmanagementsystem.common.enums.IncidentStatus.IN_PROGRESS)
            order by incident.createdAt desc, incident.id desc
            """)
    List<Incident> findOpenIncidentsByApartmentId(
            @Param("apartmentId") String apartmentId,
            Pageable pageable
    );

    @Query(
            value = """
                    select incident
                    from Incident incident
                    where incident.deleted = false
                      and incident.apartmentId = :apartmentId
                      and (:status is null or incident.status = :status)
                    order by incident.createdAt desc, incident.id desc
                    """,
            countQuery = """
                    select count(incident.id)
                    from Incident incident
                    where incident.deleted = false
                      and incident.apartmentId = :apartmentId
                      and (:status is null or incident.status = :status)
                    """
    )
    Page<Incident> findResidentIncidentsByApartmentId(
            @Param("apartmentId") String apartmentId,
            @Param("status") IncidentStatus status,
            Pageable pageable
    );

    @Query(
            value = """
                    select incident
                    from Incident incident
                    join Apartment apartment on apartment.id = incident.apartmentId
                    where incident.deleted = false
                      and apartment.deleted = false
                      and (:towerId is null or apartment.towerId = :towerId)
                      and (:apartmentId is null or incident.apartmentId = :apartmentId)
                      and (:status is null or incident.status = :status)
                      and (:query is null
                           or lower(incident.title) like :query
                           or lower(incident.area) like :query
                           or lower(incident.reporterName) like :query)
                    order by incident.createdAt desc, incident.id desc
                    """,
            countQuery = """
                    select count(incident.id)
                    from Incident incident
                    join Apartment apartment on apartment.id = incident.apartmentId
                    where incident.deleted = false
                      and apartment.deleted = false
                      and (:towerId is null or apartment.towerId = :towerId)
                      and (:apartmentId is null or incident.apartmentId = :apartmentId)
                      and (:status is null or incident.status = :status)
                      and (:query is null
                           or lower(incident.title) like :query
                           or lower(incident.area) like :query
                           or lower(incident.reporterName) like :query)
                    """
    )
    Page<Incident> searchIncidents(
            @Param("query") String query,
            @Param("towerId") String towerId,
            @Param("status") IncidentStatus status,
            @Param("apartmentId") String apartmentId,
            Pageable pageable
    );

    @Query("""
            select
                coalesce(sum(case when incident.status in
                    (org.example.emeraldmanagementsystem.common.enums.IncidentStatus.NEW,
                     org.example.emeraldmanagementsystem.common.enums.IncidentStatus.ACKNOWLEDGED,
                     org.example.emeraldmanagementsystem.common.enums.IncidentStatus.IN_PROGRESS,
                     org.example.emeraldmanagementsystem.common.enums.IncidentStatus.CLOSED,
                     org.example.emeraldmanagementsystem.common.enums.IncidentStatus.CANCELLED)
                    then 1 else 0 end), 0) as total,
                coalesce(sum(case when incident.status = org.example.emeraldmanagementsystem.common.enums.IncidentStatus.NEW then 1 else 0 end), 0) as newCount,
                coalesce(sum(case when incident.status = org.example.emeraldmanagementsystem.common.enums.IncidentStatus.ACKNOWLEDGED then 1 else 0 end), 0) as acknowledged,
                coalesce(sum(case when incident.status = org.example.emeraldmanagementsystem.common.enums.IncidentStatus.IN_PROGRESS then 1 else 0 end), 0) as inProgress,
                coalesce(sum(case when incident.status = org.example.emeraldmanagementsystem.common.enums.IncidentStatus.CLOSED then 1 else 0 end), 0) as closed,
                coalesce(sum(case when incident.status = org.example.emeraldmanagementsystem.common.enums.IncidentStatus.CANCELLED then 1 else 0 end), 0) as cancelled
            from Incident incident
            join Apartment apartment on apartment.id = incident.apartmentId
            where incident.deleted = false
              and apartment.deleted = false
              and (:towerId is null or apartment.towerId = :towerId)
            """)
    IncidentSummaryProjection getIncidentSummary(@Param("towerId") String towerId);

    interface IncidentSummaryProjection {
        Long getTotal();
        Long getNewCount();
        Long getAcknowledged();
        Long getInProgress();
        Long getClosed();
        Long getCancelled();
    }
}
