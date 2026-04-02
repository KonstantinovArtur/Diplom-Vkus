package com.example.Vkus.web;

import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.service.BuffetLookupService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalHeaderModel {

    private final BuffetLookupService buffetLookupService;
    private final CurrentUserService currentUserService;

    public GlobalHeaderModel(BuffetLookupService buffetLookupService, CurrentUserService currentUserService) {
        this.buffetLookupService = buffetLookupService;
        this.currentUserService = currentUserService;
    }

    @ModelAttribute("buffetOptions")
    public java.util.List<com.example.Vkus.web.dto.BuffetOption> buffetOptions() {
        return buffetLookupService.getActiveBuffets();
    }

    @ModelAttribute("currentBuffetId")
    public Long currentBuffetId() {
        try {
            return currentUserService.getCurrentUser().getDefaultBuffetId();
        } catch (Exception e) {
            return null;
        }
    }
}
