package com.example.Vkus.web.admin_buffet;

import com.example.Vkus.repository.UserRepository;
import com.example.Vkus.service.BuffetStatsExcelExporter;
import com.example.Vkus.service.BuffetStatsService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin-buffet/stats")
public class BuffetAdminStatsController {

    private final BuffetStatsService statsService;
    private final BuffetStatsExcelExporter excelExporter;
    private final UserRepository userRepository;

    // 🔥 ВАЖНО — ручной конструктор
    public BuffetAdminStatsController(
            BuffetStatsService statsService,
            BuffetStatsExcelExporter excelExporter,
            UserRepository userRepository
    ) {
        this.statsService = statsService;
        this.excelExporter = excelExporter;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String page(Authentication auth,
                       @RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       Model model) {

        OidcUser oidc = (OidcUser) auth.getPrincipal();
        String email = oidc.getEmail(); // ← вот это главное
        if (email == null || email.isBlank()) {
            throw new RuntimeException("OIDC email is missing");
        }

        var user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        Long buffetId = user.getDefaultBuffetId();

        LocalDate toD = (to != null) ? to : LocalDate.now();
        LocalDate fromD = (from != null) ? from : toD.minusDays(13);

        var res = statsService.getStats(buffetId, fromD, toD);

        model.addAttribute("from", fromD);
        model.addAttribute("to", toD);
        model.addAttribute("ordersTotal", res.ordersTotal());
        model.addAttribute("revenueTotal", res.revenueTotal());
        model.addAttribute("costsTotal", res.costsTotal());
        model.addAttribute("profitTotal", res.profitTotal());
        model.addAttribute("daily", res.daily());
        model.addAttribute("profitability", res.profitability());
        model.addAttribute("topProducts", res.topProducts());
        return "admin-buffet/stats";
    }

    private String getEmailFromAuth(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof OidcUser oidc) {
            String email = oidc.getAttribute("email"); // или oidc.getEmail()
            if (email != null) return email.trim();
        }
        throw new RuntimeException("OIDC email is missing");
    }


    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication auth,
                                         @RequestParam LocalDate from,
                                         @RequestParam LocalDate to) {

        String email = getEmailFromAuth(auth);

        var user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        Long buffetId = user.getDefaultBuffetId();

        var res = statsService.getStats(buffetId, from, to);
        byte[] file = excelExporter.export(res);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"buffet_stats_" + from + "_to_" + to + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }
}