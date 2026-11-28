package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.common.enums.VehicleType;
import org.example.emeraldmanagementsystem.entity.VehicleQuota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleQuotaRepository extends JpaRepository<VehicleQuota, VehicleType> {
    Optional<VehicleQuota> findByVehicleTypeAndDeletedFalse(VehicleType vehicleType);
    List<VehicleQuota> findAllByDeletedFalseOrderByVehicleTypeAsc();
}
