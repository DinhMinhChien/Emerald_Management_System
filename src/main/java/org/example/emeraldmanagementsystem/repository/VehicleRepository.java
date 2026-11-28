package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.common.enums.VehicleStatus;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;
import org.example.emeraldmanagementsystem.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {
    Optional<Vehicle> findByIdAndDeletedFalse(String id);
    boolean existsByActivePlateNumberAndDeletedFalse(String plateNumber);
    boolean existsByActiveCardNumberAndDeletedFalse(String cardNumber);
    long countByApartmentIdAndTypeAndStatusAndDeletedFalse(
            String apartmentId,
            VehicleType type,
            VehicleStatus status
    );

    @Query("""
            select vehicle.apartmentId as apartmentId,
                   vehicle.type as type,
                   count(vehicle.id) as vehicleCount
            from Vehicle vehicle
            where vehicle.deleted = false
              and vehicle.status = org.example.emeraldmanagementsystem.common.enums.VehicleStatus.ACTIVE
              and vehicle.apartmentId in :apartmentIds
            group by vehicle.apartmentId, vehicle.type
            """)
    List<ActiveVehicleCountProjection> findActiveVehicleCountsByApartmentIds(
            @Param("apartmentIds") List<String> apartmentIds
    );

    List<Vehicle> findByApartmentIdAndUserResidentIdAndStatusAndDeletedFalse(
            String apartmentId,
            String userResidentId,
            VehicleStatus status
    );

    @Query("""
            select vehicle.id as id,
                   vehicle.type as type,
                   vehicle.plateNumber as plateNumber,
                   vehicle.cardNumber as cardNumber,
                   resident.id as residentId,
                   resident.fullName as residentFullName,
                   vehicle.issuedAt as issuedAt,
                   vehicle.revokedAt as revokedAt,
                   vehicle.status as status
            from Vehicle vehicle
            left join Resident resident
              on resident.id = vehicle.userResidentId
             and resident.deleted = false
            where vehicle.deleted = false
              and vehicle.apartmentId = :apartmentId
              and (:status is null or vehicle.status = :status)
            order by vehicle.issuedAt desc, vehicle.id desc
            """)
    List<VehicleItemProjection> findVehicleItemsByApartmentId(
            @Param("apartmentId") String apartmentId,
            @Param("status") VehicleStatus status
    );

    interface VehicleItemProjection {
        String getId();
        VehicleType getType();
        String getPlateNumber();
        String getCardNumber();
        String getResidentId();
        String getResidentFullName();
        LocalDate getIssuedAt();
        LocalDate getRevokedAt();
        VehicleStatus getStatus();
    }

    interface ActiveVehicleCountProjection {
        String getApartmentId();
        VehicleType getType();
        Long getVehicleCount();
    }
}
