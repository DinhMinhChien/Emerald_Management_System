package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "invoice_items")
public class InvoiceItem extends BaseEntity {
    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id ;

    @Column(name = "invoice_id")
    private String invoiceId ;

    @Column(name = "tariff_id")
    private String tariffId ;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type")
    private FeeType feeType ;

    @Column(name = "description")
    private String description ;

    @Column(name = "quantity")
    private int quantity ;

    @Column(name = "unit_amount")
    private Long unitAmount ;

    @Column(name = "amount")
    private Long amount ;
}