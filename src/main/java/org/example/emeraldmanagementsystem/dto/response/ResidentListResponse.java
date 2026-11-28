package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;

import java.util.List;

public record ResidentListResponse(
        List<ResidentItem> items,
        long total,
        int page,
        int size
) {
    public record ResidentItem(
            String id,
            String fullName,
            String phone,
            Apartment apartment,
            ResidencyRole role,
            ResidencyRelation relation,
            String accountStatus
    ) {
    }

    public record Apartment(String id, String code, String towerCode, String towerName) {
    }
}
