package com.example.Vkus.service;

import com.example.Vkus.entity.*;
import com.example.Vkus.repository.*;
import com.example.Vkus.web.dto.MonthlyOfferForm;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BuffetAdminMonthlyOfferService {

    private final MonthlyDiscountOfferRepository offers;
    private final MonthlyDiscountOfferItemRepository items;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final UserMonthlyCategoryChoiceRepository userMonthlyChoices;


    public BuffetAdminMonthlyOfferService(MonthlyDiscountOfferRepository offers,
                                          MonthlyDiscountOfferItemRepository items,
                                          CategoryRepository categories,
                                          UserRepository users,
                                          UserMonthlyCategoryChoiceRepository userMonthlyChoices) {
        this.offers = offers;
        this.items = items;
        this.categories = categories;
        this.users = users;
        this.userMonthlyChoices = userMonthlyChoices;
    }


    public record CurrentCtx(Long userId, Long buffetId) {}

    public CurrentCtx requireCtx(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof OidcUser oidc)) {
            throw new IllegalStateException("Нет авторизации");
        }
        String email = oidc.getEmail();
        User u = users.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));

        if (u.getDefaultBuffetId() == null) {
            throw new IllegalStateException("У пользователя не задан default_buffet_id");
        }
        return new CurrentCtx(u.getId(), u.getDefaultBuffetId());
    }

    public List<Category> activeCategories() {
        return categories.findByIsActiveTrueOrderByNameAsc();
    }

    /**
     * ВАЖНО: никаких копирований items в БД тут нет.
     * Только гарантируем, что offer существует.
     */
    @Transactional
    public MonthlyDiscountOffer getOrCreateOfferWithFallback(Long buffetId, int year, int month, Long actorUserId) {
        return offers.findByBuffetIdAndYearAndMonth(buffetId, year, month)
                .orElseGet(() -> {
                    MonthlyDiscountOffer newOffer = new MonthlyDiscountOffer();
                    newOffer.setBuffetId(buffetId);
                    newOffer.setYear(year);
                    newOffer.setMonth(month);
                    newOffer.setCreatedBy(actorUserId);
                    return offers.save(newOffer);
                });
    }

    public MonthlyOfferForm toForm(MonthlyDiscountOffer offer) {
        MonthlyOfferForm f = new MonthlyOfferForm();
        f.setYear(offer.getYear());
        f.setMonth(offer.getMonth());

        // 1) items текущего оффера
        List<MonthlyDiscountOfferItem> existing = items.findByOfferIdOrderByIdAsc(offer.getId());

        // 2) если пусто — берём прошлый месяц, но ТОЛЬКО ДЛЯ ПРЕДЗАПОЛНЕНИЯ ФОРМЫ
        if (existing.isEmpty()) {
            offers.findFirstByBuffetIdOrderByYearDescMonthDesc(offer.getBuffetId())
                    .filter(prev -> !Objects.equals(prev.getId(), offer.getId()))
                    .ifPresent(prev -> existing.addAll(items.findByOfferIdOrderByIdAsc(prev.getId())));
        }

        // 3) гарантируем 4 строки
        for (int i = 0; i < 4; i++) {
            MonthlyOfferForm.Item row = new MonthlyOfferForm.Item();
            if (i < existing.size()) {
                row.setCategoryId(existing.get(i).getCategory().getId());
                row.setPercent(existing.get(i).getPercent());
            } else {
                row.setPercent(new BigDecimal("1.00"));
            }
            f.getItems().add(row);
        }

        return f;
    }

    @Transactional
    public void save(MonthlyOfferForm form, BindingResult br, Long buffetId, Long actorUserId) {

        List<MonthlyOfferForm.Item> filled = form.getItems().stream()
                .filter(it -> it.getCategoryId() != null)
                .toList();

        List<Long> ids = filled.stream().map(MonthlyOfferForm.Item::getCategoryId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            br.reject("duplicateCategories", "Категории должны быть разными (нельзя выбрать одну и ту же дважды)");
        }

        Set<Long> activeIds = activeCategories().stream().map(Category::getId).collect(Collectors.toSet());
        for (MonthlyOfferForm.Item it : filled) {
            if (!activeIds.contains(it.getCategoryId())) {
                br.reject("badCategory", "Выбрана неактивная/несуществующая категория");
                break;
            }
        }

        for (MonthlyOfferForm.Item it : filled) {
            if (it.getPercent() == null) {
                br.reject("badPercent", "Процент скидки не задан");
                break;
            }
        }

        if (br.hasErrors()) return;

        MonthlyDiscountOffer offer = offers.findByBuffetIdAndYearAndMonth(buffetId, form.getYear(), form.getMonth())
                .orElseGet(() -> {
                    MonthlyDiscountOffer o = new MonthlyDiscountOffer();
                    o.setBuffetId(buffetId);
                    o.setYear(form.getYear());
                    o.setMonth(form.getMonth());
                    o.setCreatedBy(actorUserId);
                    return offers.save(o);
                });

        // текущие категории оффера (то, что реально существует в БД)
        List<MonthlyDiscountOfferItem> existing = items.findByOfferIdOrderByIdAsc(offer.getId());
        Set<Long> existingCatIds = existing.stream()
                .map(i -> i.getCategory().getId())
                .collect(Collectors.toSet());

        Set<Long> requestedCatIds = new HashSet<>(ids);

        boolean monthLocked = userMonthlyChoices.existsByBuffetIdAndYearAndMonth(
                buffetId, form.getYear(), form.getMonth()
        );

        if (monthLocked) {
            // НЕЛЬЗЯ менять набор категорий — иначе будет 5-я, 6-я и т.д.
            if (!requestedCatIds.equals(existingCatIds)) {
                br.reject("monthLocked",
                        "Нельзя менять категории: кто-то уже выбрал категорию на этот месяц. " +
                                "Можно менять только проценты.");
                return;
            }

            // Можно менять только проценты
            Map<Long, MonthlyDiscountOfferItem> byCategoryId = existing.stream()
                    .collect(Collectors.toMap(i -> i.getCategory().getId(), i -> i));

            for (MonthlyOfferForm.Item it : filled) {
                MonthlyDiscountOfferItem e = byCategoryId.get(it.getCategoryId());
                if (e != null) e.setPercent(it.getPercent());
            }
            return;
        }

        // Если месяц НЕ зафиксирован — можно перезаписать полностью (ровно 4 категории)
        items.deleteByOfferId(offer.getId()); // у тебя уже с @Modifying flush/clear :contentReference[oaicite:2]{index=2}

        for (MonthlyOfferForm.Item it : filled) {
            Category c = categories.findById(it.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Категория не найдена: " + it.getCategoryId()));

            MonthlyDiscountOfferItem e = new MonthlyDiscountOfferItem();
            e.setOffer(offer);
            e.setCategory(c);
            e.setPercent(it.getPercent());
            items.save(e);
        }
    }



    public LocalDate currentMonth() {
        return LocalDate.now();
    }
}
