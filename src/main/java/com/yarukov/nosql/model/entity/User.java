package com.yarukov.nosql.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String fullName;

    private String phone;

    private String deliveryAddress;

    @Column(nullable = false)
    private String role;

    private LocalDateTime createdAt;

    @PrePersist
    public void init() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}