package com.example.Vkus.web.buyer;

import com.example.Vkus.entity.MonthlyDiscountOffer;
import com.example.Vkus.entity.MonthlyDiscountOfferItem;
import com.example.Vkus.entity.UserMonthlyCategoryChoice;
import com.example.Vkus.repository.MonthlyDiscountOfferItemRepository;
import com.example.Vkus.repository.MonthlyDiscountOfferRepository;
import com.example.Vkus.repository.UserMonthlyCategoryChoiceRepository;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/buyer/monthly-discount")
public class BuyerMonthlyDiscountController {

    private final CurrentUserService currentUserService;
    private final MonthlyDiscountOfferRepository offerRepository;
    private final MonthlyDiscountOfferItemRepository itemRepository;
    private final UserMonthlyCategoryChoiceRepository choiceRepository;
    private final AuditLogService audit;

    public BuyerMonthlyDiscountController(CurrentUserService currentUserService,
                                          MonthlyDiscountOfferRepository offerRepository,
                                          MonthlyDiscountOfferItemRepository itemRepository,
                                          UserMonthlyCategoryChoiceRepository choiceRepository,
                                          AuditLogService audit) {
        this.currentUserService = currentUserService;
        this.offerRepository = offerRepository;
        this.itemRepository = itemRepository;
        this.choiceRepository = choiceRepository;
        this.audit = audit;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String page(Model model) {
        model.addAttribute("noBuffet", false);
        model.addAttribute("noOffer", false);
        model.addAttribute("selectionLocked", false);

        var user = currentUserService.getCurrentUser();
        Long buffetId = user.getDefaultBuffetId();

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        model.addAttribute("year", year);
        model.addAttribute("month", month);

        if (buffetId == null) {
            model.addAttribute("noBuffet", true);
            return "buyer/monthly-discount";
        }

        var offerOpt = offerRepository.findByBuffetIdAndYearAndMonth(buffetId, year, month);
        if (offerOpt.isEmpty()) {
            model.addAttribute("noOffer", true);
            return "buyer/monthly-discount";
        }

        MonthlyDiscountOffer offer = offerOpt.get();
        List<MonthlyDiscountOfferItem> items = itemRepository.findByOfferIdOrderByIdAsc(offer.getId());

        var choiceOpt = choiceRepository.findByUserIdAndBuffetIdAndYearAndMonth(user.getId(), buffetId, year, month);
        Long selectedOfferItemId = choiceOpt.map(ch -> ch.getOfferItem().getId()).orElse(null);

        model.addAttribute("offer", offer);
        model.addAttribute("items", items);
        model.addAttribute("selectedOfferItemId", selectedOfferItemId);
        model.addAttribute("selectionLocked", choiceOpt.isPresent());

        return "buyer/monthly-discount";
    }

    @PostMapping("/select")
    @Transactional
    public String select(@RequestParam("offerItemId") Long offerItemId,
                         HttpServletRequest request) {

        var user = currentUserService.getCurrentUser();
        Long buffetId = user.getDefaultBuffetId();
        if (buffetId == null) return "redirect:/buyer/monthly-discount";

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        var offerOpt = offerRepository.findByBuffetIdAndYearAndMonth(buffetId, year, month);
        if (offerOpt.isEmpty()) return "redirect:/buyer/monthly-discount";

        MonthlyDiscountOffer offer = offerOpt.get();
        List<MonthlyDiscountOfferItem> items = itemRepository.findByOfferIdOrderByIdAsc(offer.getId());

        MonthlyDiscountOfferItem selected = items.stream()
                .filter(i -> i.getId().equals(offerItemId))
                .findFirst()
                .orElse(null);

        if (selected == null) {
            audit.log("MONTHLY_DISCOUNT_SELECT_FAIL", "monthly_offer", offer.getId(), Map.of(
                    "actorUserId", user.getId(),
                    "buffetId", buffetId,
                    "year", year,
                    "month", month,
                    "offerItemId", offerItemId,
                    "reason", "invalid_offer_item"
            ));
            return "redirect:/buyer/monthly-discount?error=invalid";
        }

        var existingOpt = choiceRepository.findByUserIdAndBuffetIdAndYearAndMonth(user.getId(), buffetId, year, month);
        if (existingOpt.isPresent()) {
            audit.log("MONTHLY_DISCOUNT_SELECT_DENIED", "monthly_offer_item", offerItemId, Map.of(
                    "actorUserId", user.getId(),
                    "buffetId", buffetId,
                    "year", year,
                    "month", month,
                    "existingOfferItemId", existingOpt.get().getOfferItem().getId(),
                    "attemptOfferItemId", offerItemId,
                    "reason", "already_selected_this_month"
            ));
            return "redirect:/buyer/monthly-discount?error=already-selected";
        }

        UserMonthlyCategoryChoice ch = new UserMonthlyCategoryChoice();
        ch.setUserId(user.getId());
        ch.setBuffetId(buffetId);
        ch.setYear(year);
        ch.setMonth(month);
        ch.setOfferItem(selected);

        choiceRepository.save(ch);

        audit.log("MONTHLY_DISCOUNT_SELECT_CREATE",
                "monthly_offer_item",
                offerItemId,
                Map.of(
                        "actorUserId", user.getId(),
                        "buffetId", buffetId,
                        "year", year,
                        "month", month,
                        "afterOfferItemId", offerItemId
                ));

        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/buyer/monthly-discount");
    }
}