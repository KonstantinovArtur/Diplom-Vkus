package com.example.Vkus.service;

import com.example.Vkus.entity.*;
import com.example.Vkus.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class CartComboService {

    private final CartRepository cartRepository;
    private final CartComboRepository cartComboRepository;
    private final ComboTemplateRepository comboTemplateRepository;
    private final CartStockValidationService cartStockValidationService;

    public CartComboService(CartRepository cartRepository,
                            CartComboRepository cartComboRepository,
                            ComboTemplateRepository comboTemplateRepository,
                            CartStockValidationService cartStockValidationService) {
        this.cartRepository = cartRepository;
        this.cartComboRepository = cartComboRepository;
        this.comboTemplateRepository = comboTemplateRepository;
        this.cartStockValidationService = cartStockValidationService;
    }

    @Transactional(readOnly = true)
    public List<CartCombo> getCombos(Long userId, Long buffetId) {
        var cartOpt = cartRepository.findByUserIdAndBuffetId(userId, buffetId);
        if (cartOpt.isEmpty()) return List.of();
        return cartComboRepository.findAllByCartIdFull(cartOpt.get().getId());
    }

    /**
     * Добавляет в корзину ОДНУ конфигурацию комбо.
     * selectedProducts: key=slotId, value=productId (по одному товару на слот).
     */
    @Transactional
    public void addCombo(Long userId, Long buffetId, Long comboTemplateId, int qty, Map<Long, Long> selectedProducts) {
        if (qty <= 0) qty = 1;

        Cart cart = cartRepository.findByUserIdAndBuffetId(userId, buffetId)
                .orElseGet(() -> {
                    Cart c = new Cart();
                    c.setUserId(userId);
                    c.setBuffetId(buffetId);
                    return cartRepository.save(c);
                });

        ComboTemplate tpl = comboTemplateRepository.findByIdFull(comboTemplateId)
                .orElseThrow(() -> new IllegalArgumentException("Combo template not found: " + comboTemplateId));

        for (ComboSlot slot : tpl.getSlots()) {
            Long pid = selectedProducts.get(slot.getId());
            if (pid == null) {
                throw new IllegalStateException("Не выбран товар для слота: " + slot.getName());
            }

            boolean allowed = slot.getProducts().stream().anyMatch(sp -> sp.getProduct().getId().equals(pid));
            if (!allowed) {
                throw new IllegalStateException("Товар не разрешён в слоте: " + slot.getName());
            }
        }

        Map<Long, Integer> comboNeedByProduct = new HashMap<>();
        for (ComboSlot slot : tpl.getSlots()) {
            Long productId = selectedProducts.get(slot.getId());
            comboNeedByProduct.merge(productId, qty, Integer::sum);
        }

        cartStockValidationService.validateCanAddCombo(userId, buffetId, comboNeedByProduct);

        BigDecimal extrasSum = BigDecimal.ZERO;

        CartCombo cartCombo = new CartCombo();
        cartCombo.setCart(cart);
        cartCombo.setComboTemplate(tpl);
        cartCombo.setQty(qty);

        List<CartComboItem> items = new ArrayList<>();

        for (ComboSlot slot : tpl.getSlots()) {
            Long productId = selectedProducts.get(slot.getId());

            ComboSlotProduct slotProduct = slot.getProducts().stream()
                    .filter(sp -> sp.getProduct().getId().equals(productId))
                    .findFirst()
                    .orElseThrow();

            BigDecimal extra = slotProduct.getExtraPrice();
            if (extra != null) extrasSum = extrasSum.add(extra);

            CartComboItem it = new CartComboItem();
            it.setCartCombo(cartCombo);
            it.setComboSlot(slot);
            it.setProduct(slotProduct.getProduct());
            it.setQty(1);
            it.setExtraPriceSnapshot(extra);
            items.add(it);
        }

        cartCombo.setItems(items);

        BigDecimal finalUnit = nz(tpl.getBasePrice()).add(extrasSum);
        cartCombo.setComboPriceSnapshot(scale2(finalUnit));

        cartComboRepository.save(cartCombo);
    }

    @Transactional
    public void removeCombo(Long userId, Long buffetId, Long cartComboId) {
        Cart cart = cartRepository.findByUserIdAndBuffetId(userId, buffetId)
                .orElseThrow(() -> new IllegalStateException("Cart not found"));
        CartCombo cc = cartComboRepository.findById(cartComboId).orElseThrow();
        if (!cc.getCart().getId().equals(cart.getId())) {
            throw new IllegalStateException("Combo not in this cart");
        }
        cartComboRepository.delete(cc);
    }

    @Transactional
    public void clearCombos(Long userId, Long buffetId) {
        var cartOpt = cartRepository.findByUserIdAndBuffetId(userId, buffetId);
        if (cartOpt.isEmpty()) return;
        cartComboRepository.deleteByCart_Id(cartOpt.get().getId());
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static BigDecimal scale2(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}
