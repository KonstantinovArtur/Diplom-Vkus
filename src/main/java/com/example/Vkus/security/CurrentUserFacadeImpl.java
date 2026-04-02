package com.example.Vkus.security;

import com.example.Vkus.security.CurrentUserService;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserFacadeImpl implements CurrentUserFacade {

    private final CurrentUserService currentUserService;

    public CurrentUserFacadeImpl(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public Long requireUserId() {
        return currentUserService.getCurrentUser().getId();
    }

    @Override
    public Long requireBuffetId() {
        return currentUserService.getCurrentBuffetIdOrThrow();
    }
}
