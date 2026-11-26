package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "billing_periods")
public class BillingPeriod extends BaseEntity {
    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id ;

    @Column(name = "year")
    private int year ;

    @Column(name = "month")
    private int month ;

    @Column(name = "due_date")
    private LocalDate dueDate ;
}
