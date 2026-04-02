package com.example.Vkus.mobile.auth;

public class TooManyMobileAuthAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyMobileAuthAttemptsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}