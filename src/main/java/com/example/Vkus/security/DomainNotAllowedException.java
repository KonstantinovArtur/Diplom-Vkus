package com.example.Vkus.security;

import org.springframework.security.core.AuthenticationException;

public class DomainNotAllowedException extends AuthenticationException {
    public DomainNotAllowedException() {
        super("DOMAIN_NOT_ALLOWED");
    }
}