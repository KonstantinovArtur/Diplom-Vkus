package com.example.Vkus.mobile.auth.dto;

import com.example.Vkus.entity.User;

import java.util.List;

public record MobileUserDto(
        Long id,
        String email,
        String fullName,
        String status,
        Long defaultBuffetId,
        List<String> roles
) {
    public static MobileUserDto from(User user, List<String> roles) {
        return new MobileUserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                user.getDefaultBuffetId(),
                roles == null ? List.of() : List.copyOf(roles)
        );
    }
}