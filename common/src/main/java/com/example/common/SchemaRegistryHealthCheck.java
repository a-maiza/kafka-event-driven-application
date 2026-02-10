package com.example.common;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fail-fast check for Schema Registry connectivity in non-dev profiles.
 * Prevents application startup if Schema Registry is unreachable in staging/prod.
 */
@Component
@Profile({"staging", "prod"})
public class SchemaRegistryHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(SchemaRegistryHealthCheck.class);

    @Value("${spring.kafka.properties.schema.registry.url:}")
    private String schemaRegistryUrl;

    @PostConstruct
    public void verifySchemaRegistryConnectivity() {
        if (schemaRegistryUrl == null || schemaRegistryUrl.isBlank()) {
            log.warn("Schema Registry URL not configured; skipping connectivity check");
            return;
        }

        log.info("Verifying Schema Registry connectivity at {}", schemaRegistryUrl);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(schemaRegistryUrl + "/config"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Schema Registry is reachable (status={})", response.statusCode());
            } else {
                throw new IllegalStateException(
                        "Schema Registry returned unexpected status " + response.statusCode()
                                + " at " + schemaRegistryUrl);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Schema Registry is unreachable at " + schemaRegistryUrl
                            + ". Application startup aborted.", e);
        }
    }
}
