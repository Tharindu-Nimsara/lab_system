package com.lab.backend.report;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Letterhead details printed on bills and reports; overridable via env. */
@Component
@Getter
public class LabInfo {

    @Value("${app.lab.name}")
    private String name;

    @Value("${app.lab.address}")
    private String address;

    @Value("${app.lab.phone}")
    private String phone;
}
