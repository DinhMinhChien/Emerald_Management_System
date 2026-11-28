package org.example.emeraldmanagementsystem.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;
import org.example.emeraldmanagementsystem.common.enums.VoidReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record InvoiceDetailResponse(
        String id,
        Period period,
        Apartment apartment,
        Recipient recipient,
        BigDecimal areaM2,
        String serviceTierName,
        InvoiceStatus status,
        String displayStatus,
        LocalDate dueDate,
        OffsetDateTime issuedAt,
        List<InvoiceItem> items,
        Long totalAmount,
        Payment payment,
        @JsonProperty("void") VoidInfo voidInfo
) {
    public record Period(String id, int year, int month) {
    }

    public record Apartment(String id, String code, String towerCode, String towerName) {
    }

    public record Recipient(String residentId, String fullName) {
    }

    public record InvoiceItem(
            FeeType feeType,
            String description,
            int quantity,
            Long unitAmount,
            Long amount
    ) {
    }

    public record Payment(LocalDate paidOn, String receivedBy, String note) {
    }

    public record VoidInfo(
            VoidReason reason,
            String note,
            OffsetDateTime voidedAt,
            String voidedBy
    ) {
    }
}
