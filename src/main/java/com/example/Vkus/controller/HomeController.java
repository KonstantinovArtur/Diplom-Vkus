package com.example.Vkus.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication auth) {
        System.out.println("HOME auth.name=" + (auth == null ? null : auth.getName()));
        System.out.println("HOME authorities=" + (auth == null ? null : auth.getAuthorities()));
        if (auth == null) return "redirect:/login";

        boolean isDbAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DB_ADMIN"));
        if (isDbAdmin) return "redirect:/admin-db/users";

        boolean isBuffetAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_BUFFET_ADMIN"));
        if (isBuffetAdmin) return "redirect:/admin-buffet/discounts";

        boolean isWarehouse = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_WAREHOUSE"));
        if (isWarehouse) return "redirect:/warehouse/invoices";

        boolean isSeller = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SELLER"));
        if (isSeller) return "redirect:/seller/stocks";

        // buyer / неизвестное — пока на каталог
        return "redirect:/menu";
    }
}
