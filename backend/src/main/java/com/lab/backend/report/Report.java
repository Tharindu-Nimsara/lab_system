package com.lab.backend.report;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "invoice_id", nullable = false, unique = true)
    private Long invoiceId;

    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    @Column(name = "sent_email_at")
    private OffsetDateTime sentEmailAt;

    @Column(name = "sent_whatsapp_at")
    private OffsetDateTime sentWhatsappAt;
}
