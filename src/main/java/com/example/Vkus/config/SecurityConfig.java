package com.example.Vkus.config;

import com.example.Vkus.mobile.security.MobileJwtAuthenticationConverter;
import com.example.Vkus.security.WebLoginBlockFilter;
import com.example.Vkus.security.WebOAuth2FailureHandler;
import com.example.Vkus.security.WebOAuth2SuccessHandler;
import com.example.Vkus.service.DbRolesOidcUserService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(BackupProperties.class)
public class SecurityConfig {

    private final DbRolesOidcUserService dbRolesOidcUserService;
    private final JwtDecoder mobileJwtDecoder;
    private final MobileJwtAuthenticationConverter mobileJwtAuthenticationConverter;

    private final WebOAuth2SuccessHandler webOAuth2SuccessHandler;
    private final WebOAuth2FailureHandler webOAuth2FailureHandler;
    private final WebLoginBlockFilter webLoginBlockFilter;

    public SecurityConfig(
            DbRolesOidcUserService dbRolesOidcUserService,
            JwtDecoder mobileJwtDecoder,
            MobileJwtAuthenticationConverter mobileJwtAuthenticationConverter,
            WebOAuth2SuccessHandler webOAuth2SuccessHandler,
            WebOAuth2FailureHandler webOAuth2FailureHandler,
            WebLoginBlockFilter webLoginBlockFilter
    ) {
        this.dbRolesOidcUserService = dbRolesOidcUserService;
        this.mobileJwtDecoder = mobileJwtDecoder;
        this.mobileJwtAuthenticationConverter = mobileJwtAuthenticationConverter;
        this.webOAuth2SuccessHandler = webOAuth2SuccessHandler;
        this.webOAuth2FailureHandler = webOAuth2FailureHandler;
        this.webLoginBlockFilter = webLoginBlockFilter;
    }

    @Bean
    @Order(1)
    SecurityFilterChain mobileApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/mobile/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/mobile/auth/google").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt
                                .decoder(mobileJwtDecoder)
                                .jwtAuthenticationConverter(mobileJwtAuthenticationConverter)
                        )
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                );

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/403", "/css/**", "/js/**", "/images/**", "/products/**", "/combos/*/image").permitAll()
                        .requestMatchers("/admin-db/**").hasRole("DB_ADMIN")
                        .requestMatchers("/admin-buffet/**").hasRole("BUFFET_ADMIN")
                        .requestMatchers("/warehouse/**").hasRole("WAREHOUSE")
                        .requestMatchers("/seller/**").hasRole("SELLER")
                        .anyRequest().authenticated()
                )

                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(u -> u.oidcUserService(dbRolesOidcUserService))
                        .successHandler(webOAuth2SuccessHandler)
                        .failureHandler(webOAuth2FailureHandler)
                )

                .logout(l -> l.logoutSuccessUrl("/login?logout"))
                .exceptionHandling(e -> e.accessDeniedPage("/403"));

        http.addFilterBefore(webLoginBlockFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}