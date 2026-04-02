package com.example.Vkus.mobile.combo;

import com.example.Vkus.entity.ComboSlot;
import com.example.Vkus.entity.ComboTemplate;
import com.example.Vkus.mobile.cart.dto.MobileCartActionResponse;
import com.example.Vkus.mobile.combo.dto.*;
import com.example.Vkus.repository.ComboTemplateRepository;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.CartComboService;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MobileComboService {

    private final ComboTemplateRepository comboTemplateRepository;
    private final CartComboService cartComboService;
    private final AuditLogService audit;

    public MobileComboService(ComboTemplateRepository comboTemplateRepository,
                              CartComboService cartComboService,
                              AuditLogService audit) {
        this.comboTemplateRepository = comboTemplateRepository;
        this.cartComboService = cartComboService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public MobileComboListResponse getCombos(Jwt jwt) {
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));
        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        var combos = comboTemplateRepository.findActiveByBuffet(buffetId).stream()
                .map(c -> new MobileComboSummaryDto(
                        c.getId(),
                        c.getName(),
                        c.getBasePrice()
                ))
                .toList();

        return new MobileComboListResponse(buffetId, combos);
    }

    @Transactional(readOnly = true)
    public MobileComboDetailResponse getComboDetail(Jwt jwt, Long comboId) {
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));
        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        ComboTemplate tpl = comboTemplateRepository.findByIdFull(comboId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Комбо не найдено"));

        if (!tpl.getBuffet().getId().equals(buffetId) || !Boolean.TRUE.equals(tpl.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Комбо недоступно");
        }

        var slots = tpl.getSlots().stream()
                .sorted(Comparator
                        .comparing(ComboSlot::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ComboSlot::getId))
                .map(slot -> new MobileComboSlotDto(
                        slot.getId(),
                        slot.getName(),
                        slot.getRequiredQty(),
                        slot.getSortOrder(),
                        slot.getProducts().stream()
                                .sorted(Comparator.comparing(sp -> sp.getProduct().getName(), String.CASE_INSENSITIVE_ORDER))
                                .map(sp -> new MobileComboSlotOptionDto(
                                        sp.getProduct().getId(),
                                        sp.getProduct().getName(),
                                        (sp.getProduct().getImageData() != null && sp.getProduct().getImageData().length > 0)
                                                ? "/products/" + sp.getProduct().getId() + "/image"
                                                : null,
                                        sp.getExtraPrice()
                                ))
                                .toList()
                ))
                .toList();

        return new MobileComboDetailResponse(
                tpl.getId(),
                tpl.getName(),
                tpl.getBasePrice(),
                slots
        );
    }

    @Transactional
    public MobileCartActionResponse addComboToCart(Jwt jwt, Long comboId, MobileAddComboToCartRequest request) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        int qty = (request.qty() == null || request.qty() <= 0) ? 1 : request.qty();

        Map<Long, Long> selected = request.selections().stream()
                .collect(Collectors.toMap(
                        MobileComboSelectionDto::slotId,
                        MobileComboSelectionDto::productId,
                        (a, b) -> b
                ));

        try {
            cartComboService.addCombo(userId, buffetId, comboId, qty, selected);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        audit.log("MOBILE_CART_ADD_COMBO", "combo_template", comboId, Map.of(
                "actorUserId", userId,
                "buffetId", buffetId,
                "qty", qty,
                "selected", selected
        ));

        return new MobileCartActionResponse(true, "Комбо добавлено в корзину");
    }

    private Long extractLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Некорректный uid в токене");
    }

    private Long extractOptionalLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) return Long.parseLong(s);
        return null;
    }
}