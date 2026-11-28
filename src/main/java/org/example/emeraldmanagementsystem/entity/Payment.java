package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.enums.PaymentMethod;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id;

    @Column(name = "invoice_id")
    private String invoiceId;

    @Column(name = "amount")
    private long amount;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "method")
    private PaymentMethod method = PaymentMethod.CASH;

    @Column(name = "reference")
    private String reference;

    @Column(name = "note")
    private String note;


}
