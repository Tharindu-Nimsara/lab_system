package com.lab.backend.patient;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_no", nullable = false, unique = true)
    private String patientNo;

    @Column(nullable = false)
    private String name;

    @Column(name = "nic_or_id")
    private String nicOrId;

    private LocalDate dob;

    private String gender;

    @Column(nullable = false)
    private String phone;

    private String email;

    private String address;

    @Column(name = "consent_email", nullable = false)
    private boolean consentEmail;

    @Column(name = "consent_whatsapp", nullable = false)
    private boolean consentWhatsapp;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
