package com.nagarro.eclaims.partner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * eClaims — Partner Service (port 8084)
 *
 * Geo-radius surveyor + workshop matching.
 * Consumes: claim.validated, assessment.submitted
 * Produces: surveyor.assigned, workshop.assigned, repair.status.updated, repair.completed
 */
@SpringBootApplication
public class PartnerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PartnerServiceApplication.class, args);
    }
}
