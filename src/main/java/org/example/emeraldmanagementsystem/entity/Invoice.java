package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.InvoiceStatus;
import org.example.emeraldmanagementsystem.common.VoidReason;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Invoice extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id;

    @Column(name = "period_id")
    private String periodId;

    @Column(name = "apartment_id")
    private String apartmentId;

    @Column(name = "recipient_resident_id")
    private String recipientResidentId;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "area_m2")
    private BigDecimal areaM2;

    @Column(name = "service_tier_name")
    private String serviceTierName;

    @Column(name = "total_amount")
    private Long totalAmount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private InvoiceStatus status;

    @Column(name = "paid_on")
    private LocalDate paidOn ;

    @Column(name = "paid_note")
    private String paidNote ;

    @Column(name = "received_by")
    private String receivedBy ;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt ;

    @Enumerated(EnumType.STRING)
    @Column(name = "void_reason")
    private VoidReason voidReason ;

    @Column(name = "void_note")
    private String voidNote ;

    @Column(name = "active_apartment_id", length = 36, insertable = false, updatable = false)
    private String activeApartmentId;
}
