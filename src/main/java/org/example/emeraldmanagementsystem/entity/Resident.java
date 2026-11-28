package org.example.emeraldmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.emeraldmanagementsystem.common.enums.Gender;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;

@Entity
@Table(name = "residents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Resident extends BaseEntity{

    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "national_id")
    private String nationalId;

}
