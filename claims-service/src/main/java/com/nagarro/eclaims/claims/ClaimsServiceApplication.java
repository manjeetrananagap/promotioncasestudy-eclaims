package com.nagarro.eclaims.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * eClaims — Claims Service  (port 8081)
 *
 * Run locally:
 *   cd eclaims && mvn clean install -DskipTests
 *   cd claims-service && mvn spring-boot:run
 */
@SpringBootApplication
@EnableKafka
public class ClaimsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClaimsServiceApplication.class, args);
    }
}
