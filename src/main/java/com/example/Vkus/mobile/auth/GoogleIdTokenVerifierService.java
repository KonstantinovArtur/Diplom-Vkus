package com.example.Vkus.mobile.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class GoogleIdTokenVerifierService {

    private final JwtDecoder googleJwtDecoder;
    private final String googleClientId;

    public GoogleIdTokenVerifierService(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(15000);
        requestFactory.setReadTimeout(60000);

        RestTemplate restTemplate = new RestTemplate(requestFactory);

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .restOperations(restTemplate)
                .build();

        decoder.setJwtValidator(JwtValidators.createDefault());

        this.googleJwtDecoder = decoder;
        this.googleClientId = googleClientId;
    }

    public GoogleUserInfo verify(String idToken) {
        Jwt jwt;

        try {
            jwt = googleJwtDecoder.decode(idToken);
        } catch (JwtException ex) {
            ex.printStackTrace();
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Неверный Google ID token: " + ex.getMessage()
            );
        }

        validateIssuer(jwt);
        validateAudience(jwt);

        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        String sub = jwt.getSubject();
        boolean emailVerified = toBoolean(jwt.getClaims().get("email_verified"));

        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "В Google token отсутствует email");
        }

        email = email.toLowerCase(Locale.ROOT);


        if (!emailVerified) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email не подтверждён");
        }

        if (!StringUtils.hasText(sub)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "В Google token отсутствует sub");
        }

        if (!StringUtils.hasText(name)) {
            name = email;
        }

        return new GoogleUserInfo(email, name, sub);
    }

    private void validateIssuer(Jwt jwt) {
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();

        if (!"https://accounts.google.com".equals(issuer) && !"accounts.google.com".equals(issuer)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный issuer Google token");
        }
    }

    private void validateAudience(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(googleClientId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token выпущен не для этого client_id");
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    public record GoogleUserInfo(
            String email,
            String fullName,
            String googleSub
    ) {
    }
}