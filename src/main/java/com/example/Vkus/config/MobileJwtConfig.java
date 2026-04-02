package com.example.Vkus.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class MobileJwtConfig {

    @Bean
    public SecretKey mobileJwtSecretKey(@Value("${app.mobile.jwt.secret}") String rawSecret) {
        byte[] keyBytes = rawSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.mobile.jwt.secret должен быть не короче 32 байт");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder mobileJwtEncoder(SecretKey mobileJwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(mobileJwtSecretKey));
    }

    @Bean
    public JwtDecoder mobileJwtDecoder(SecretKey mobileJwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(mobileJwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(JwtValidators.createDefault());
        return decoder;
    }
}