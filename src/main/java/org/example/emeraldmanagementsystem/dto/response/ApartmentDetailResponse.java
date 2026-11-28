package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.ResidencyEndReason;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ApartmentDetailResponse(
        String id,
        String code,
        Tower tower,
        int floor,
        BigDecimal areaM2,
        int bedrooms,
        int bathrooms,
        ApartmentStatus status,
        String serviceTierName,
        Head head,
        long occupantCount,
        Debt debt,
        List<ResidencyHistoryItem> residencyHistory
) {
    public record Tower(String id, String code, String name) {
    }

    public record Head(String residentId, String fullName, String phone, LocalDate since) {
    }

    public record Debt(long totalAmount, long pendingInvoiceCount, long overdueInvoiceCount, long overdueAmount) {
    }

    public record ResidencyHistoryItem(
            String residencyId,
            Resident resident,
            ResidencyRole role,
            ResidencyRelation relation,
            LocalDate startDate,
            LocalDate endDate,
            ResidencyEndReason endReason
    ) {
    }

    public record Resident(String id, String fullName) {
    }
}
