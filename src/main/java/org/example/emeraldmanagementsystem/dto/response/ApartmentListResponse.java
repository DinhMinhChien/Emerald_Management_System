package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;

import java.math.BigDecimal;
import java.util.List;

public record ApartmentListResponse(
        List<ApartmentItem> items,
        long total,
        int page,
        int size
) {
    public record ApartmentItem(
            String id,
            String code,
            Tower tower,
            int floor,
            BigDecimal areaM2,
            ApartmentStatus status,
            Head head,
            long debtAmount,
            long overdueInvoiceCount
    ) {
    }

    public record Tower(String id, String code, String name) {
    }

    public record Head(String residentId, String fullName, String phone) {
    }
}
