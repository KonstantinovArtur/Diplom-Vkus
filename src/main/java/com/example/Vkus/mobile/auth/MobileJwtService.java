package com.example.Vkus.mobile.auth;
import com.example.Vkus.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MobileJwtService {

    private final JwtEncoder jwtEncoder;
    private final long ttlMinutes;

    public MobileJwtService(
            JwtEncoder jwtEncoder,
            @Value("${app.mobile.jwt.ttl-minutes:43200}") long ttlMinutes
    ) {
        this.jwtEncoder = jwtEncoder;
        this.ttlMinutes = ttlMinutes;
    }

    public String generateToken(User user, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttlMinutes, ChronoUnit.MINUTES);

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("vkus-mobile-api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("status", user.getStatus())
                .claim("roles", roles == null ? List.of() : roles);

        if (user.getDefaultBuffetId() != null) {
            claimsBuilder.claim("defaultBuffetId", user.getDefaultBuffetId());
        }

        JwtEncoderParameters parameters = JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claimsBuilder.build()
        );

        return jwtEncoder.encode(parameters).getTokenValue();
    }

    public long getExpiresInSeconds() {
        return ttlMinutes * 60;
    }
}