package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.InvoiceStatus;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    private int totalAmount;

    @Column(name = "paid_amount")
    private int paidAmount = 0;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private InvoiceStatus status;
}
