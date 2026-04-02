package com.example.Vkus.repository;

import com.example.Vkus.entity.AuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {
    Optional<AuthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}
