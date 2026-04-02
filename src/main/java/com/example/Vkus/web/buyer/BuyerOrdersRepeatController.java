package com.example.Vkus.web.buyer;

import com.example.Vkus.security.CurrentUserFacade;
import com.example.Vkus.service.RepeatOrderService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/buyer/orders")
public class BuyerOrdersRepeatController {

    private final CurrentUserFacade currentUser;
    private final RepeatOrderService repeatOrderService;

    public BuyerOrdersRepeatController(CurrentUserFacade currentUser,
                                       RepeatOrderService repeatOrderService) {
        this.currentUser = currentUser;
        this.repeatOrderService = repeatOrderService;
    }

    @PostMapping("/{orderId}/repeat")
    public String repeat(@PathVariable Long orderId, RedirectAttributes ra) {

        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        var r = repeatOrderService.repeat(userId, buffetId, orderId);

        if (!r.skippedProductIds().isEmpty()) {
            ra.addFlashAttribute("warn",
                    "Добавлено позиций: " + r.addedPositions() +
                            ". Некоторые товары недоступны: " + r.skippedProductIds());
        } else {
            ra.addFlashAttribute("ok", "Товары из заказа добавлены в корзину.");
        }

        return "redirect:/cart";
    }
}