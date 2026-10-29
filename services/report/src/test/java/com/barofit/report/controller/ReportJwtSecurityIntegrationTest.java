package com.barofit.report.controller;

import com.barofit.report.service.ReportService;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReportJwtSecurityIntegrationTest {

    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final KeyPair OTHER_KEY_PAIR = generateKeyPair();
    private static final Path JWKS_FILE = createJwksFile();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReportService reportService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("barofit.jwt.public-jwks-path", () -> JWKS_FILE.toString());
        registry.add("spring.datasource.url",
            () -> "jdbc:postgresql://localhost:5432/barofit_report_dev");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/reports/sessions/" +
                UUID.randomUUID() + "/summary"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtIsAccepted() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(reportService.getSessionSummary(userId, sessionId))
            .thenReturn(Map.of("setCount", 1));

        mvc.perform(get("/api/v1/reports/sessions/" +
                sessionId + "/summary")
                .header("Authorization",
                    "Bearer " + token(KEY_PAIR, userId,
                        "barofit-identity", "barofit-api")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.setCount").value(1));

        verify(reportService).getSessionSummary(userId, sessionId);
    }

    @Test
    void invalidIssuerIsRejected() throws Exception {
        mvc.perform(get("/api/v1/reports/sessions/" +
                UUID.randomUUID() + "/summary")
                .header("Authorization",
                    "Bearer " + token(KEY_PAIR, UUID.randomUUID(),
                        "wrong-issuer", "barofit-api")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAudienceIsRejected() throws Exception {
        mvc.perform(get("/api/v1/reports/sessions/" +
                UUID.randomUUID() + "/summary")
                .header("Authorization",
                    "Bearer " + token(KEY_PAIR, UUID.randomUUID(),
                        "barofit-identity", "wrong-audience")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        mvc.perform(get("/api/v1/reports/sessions/" +
                UUID.randomUUID() + "/summary")
                .header("Authorization",
                    "Bearer " + token(OTHER_KEY_PAIR, UUID.randomUUID(),
                        "barofit-identity", "barofit-api")))
            .andExpect(status().isUnauthorized());
    }

    private static String token(
        KeyPair keyPair, UUID userId, String issuer, String audience
    ) throws Exception {
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(userId.toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(900)))
            .build();

        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID("report-test-key")
                .build(),
            claims
        );

        jwt.sign(new RSASSASigner(keyPair.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path createJwksFile() {
        try {
            RSAKey key = new RSAKey.Builder(
                (java.security.interfaces.RSAPublicKey)
                    KEY_PAIR.getPublic())
                .keyID("report-test-key")
                .build();

            Path path = Files.createTempFile(
                "barofit-report-jwks-", ".json");

            Files.writeString(
                path,
                new JWKSet(key).toString()
            );

            path.toFile().deleteOnExit();
            return path;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}