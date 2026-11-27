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

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User extends BaseEntity{
    @Id
    @UuidGenerator
    @Column(name = "id")
    private String id ;

    @Column(name = "role")
    private String role ;

    @Column(name = "username")
    private String username ;

    @Column(name = "full_name")
    private String fullName ;

    @Column(name = "must_change_password")
    private boolean mustChangePassword = true ;

    @Column(name = "status")
    private String status ;

    @Column(name = "resident_id")
    private String residentId ;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt ;
}
