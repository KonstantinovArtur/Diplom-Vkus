package com.example.Vkus.mobile.auth;

import com.example.Vkus.entity.User;
import com.example.Vkus.mobile.auth.dto.MobileAuthResponse;
import com.example.Vkus.mobile.auth.dto.MobileBuffetOptionDto;
import com.example.Vkus.mobile.auth.dto.MobileUserDto;
import com.example.Vkus.repository.BuffetRepository;
import com.example.Vkus.repository.RoleRepository;
import com.example.Vkus.repository.UserRepository;
import com.example.Vkus.service.BuffetLookupService;
import com.example.Vkus.service.OAuthUserProvisioningService;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MobileAuthService {

    private final GoogleIdTokenVerifierService googleIdTokenVerifierService;
    private final OAuthUserProvisioningService provisioningService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final MobileJwtService mobileJwtService;
    private final BuffetLookupService buffetLookupService;
    private final BuffetRepository buffetRepository;
    private final MobileLoginAttemptService mobileLoginAttemptService;

    public MobileAuthService(
            GoogleIdTokenVerifierService googleIdTokenVerifierService,
            OAuthUserProvisioningService provisioningService,
            RoleRepository roleRepository,
            UserRepository userRepository,
            MobileJwtService mobileJwtService,
            BuffetLookupService buffetLookupService,
            BuffetRepository buffetRepository,
            MobileLoginAttemptService mobileLoginAttemptService
    ) {
        this.googleIdTokenVerifierService = googleIdTokenVerifierService;
        this.provisioningService = provisioningService;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.mobileJwtService = mobileJwtService;
        this.buffetLookupService = buffetLookupService;
        this.buffetRepository = buffetRepository;
        this.mobileLoginAttemptService = mobileLoginAttemptService;
    }

    @Transactional
    public MobileAuthResponse authenticateWithGoogle(String idToken, String clientIp) {
        try {
            mobileLoginAttemptService.checkAllowed(clientIp);

            GoogleIdTokenVerifierService.GoogleUserInfo googleUser =
                    googleIdTokenVerifierService.verify(idToken);

            User user;
            try {
                user = provisioningService.provisionGoogleUser(
                        googleUser.email(),
                        googleUser.fullName(),
                        googleUser.googleSub()
                );
            } catch (RuntimeException ex) {
                if ("USER_BLOCKED".equalsIgnoreCase(ex.getMessage())) {
                    mobileLoginAttemptService.onFailure(clientIp, "user_blocked");
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь заблокирован");
                }
                mobileLoginAttemptService.onFailure(clientIp, "provisioning_error");
                throw ex;
            }

            List<String> roles = roleRepository.findRoleCodesByUserId(user.getId());
            String accessToken = mobileJwtService.generateToken(user, roles);

            mobileLoginAttemptService.onSuccess(clientIp);

            return new MobileAuthResponse(
                    accessToken,
                    "Bearer",
                    mobileJwtService.getExpiresInSeconds(),
                    MobileUserDto.from(user, roles)
            );

        } catch (TooManyMobileAuthAttemptsException ex) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ex.getMessage() + " Осталось ждать: " + ex.getRetryAfterSeconds() + " сек."
            );
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                    ex.getStatusCode() == HttpStatus.FORBIDDEN ||
                    ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                mobileLoginAttemptService.onFailure(clientIp, "response_status_" + ex.getStatusCode().value());
            }
            throw ex;
        } catch (Exception ex) {
            mobileLoginAttemptService.onFailure(clientIp, "invalid_google_auth");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ошибка авторизации через Google");
        }
    }

    @Transactional(readOnly = true)
    public MobileUserDto getCurrentUser(Jwt jwt) {
        Long userId = extractLong(jwt.getClaims().get("uid"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден"));

        if ("blocked".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь заблокирован");
        }

        List<String> roles = roleRepository.findRoleCodesByUserId(userId);
        return MobileUserDto.from(user, roles);
    }

    @Transactional(readOnly = true)
    public List<MobileBuffetOptionDto> getAvailableBuffets() {
        return buffetLookupService.getActiveBuffets().stream()
                .map(b -> new MobileBuffetOptionDto(b.id(), b.label()))
                .toList();
    }

    @Transactional
    public MobileAuthResponse selectBuffet(Jwt jwt, Long buffetId) {
        Long userId = extractLong(jwt.getClaims().get("uid"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден"));

        if ("blocked".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь заблокирован");
        }

        var buffet = buffetRepository.findById(buffetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Буфет не найден"));

        if (Boolean.FALSE.equals(buffet.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Буфет неактивен");
        }

        userRepository.updateDefaultBuffet(userId, buffetId);

        user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден"));

        List<String> roles = roleRepository.findRoleCodesByUserId(userId);
        String accessToken = mobileJwtService.generateToken(user, roles);

        return new MobileAuthResponse(
                accessToken,
                "Bearer",
                mobileJwtService.getExpiresInSeconds(),
                MobileUserDto.from(user, roles)
        );
    }

    private Long extractLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Некорректный uid в токене");
    }
}