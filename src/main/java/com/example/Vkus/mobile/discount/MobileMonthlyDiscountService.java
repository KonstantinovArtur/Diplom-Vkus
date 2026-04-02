package com.example.Vkus.mobile.discount;

import com.example.Vkus.entity.MonthlyDiscountOffer;
import com.example.Vkus.entity.MonthlyDiscountOfferItem;
import com.example.Vkus.entity.UserMonthlyCategoryChoice;
import com.example.Vkus.mobile.discount.dto.MobileChooseMonthlyDiscountResponse;
import com.example.Vkus.mobile.discount.dto.MobileMonthlyDiscountCategoryDto;
import com.example.Vkus.mobile.discount.dto.MobileMonthlyDiscountResponse;
import com.example.Vkus.repository.MonthlyDiscountOfferItemRepository;
import com.example.Vkus.repository.MonthlyDiscountOfferRepository;
import com.example.Vkus.repository.UserMonthlyCategoryChoiceRepository;
import com.example.Vkus.service.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MobileMonthlyDiscountService {

    private final MonthlyDiscountOfferRepository offerRepository;
    private final MonthlyDiscountOfferItemRepository itemRepository;
    private final UserMonthlyCategoryChoiceRepository choiceRepository;
    private final AuditLogService audit;

    public MobileMonthlyDiscountService(MonthlyDiscountOfferRepository offerRepository,
                                        MonthlyDiscountOfferItemRepository itemRepository,
                                        UserMonthlyCategoryChoiceRepository choiceRepository,
                                        AuditLogService audit) {
        this.offerRepository = offerRepository;
        this.itemRepository = itemRepository;
        this.choiceRepository = choiceRepository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public MobileMonthlyDiscountResponse getCategories(Jwt jwt) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        var offerOpt = offerRepository.findByBuffetIdAndYearAndMonth(buffetId, year, month);
        if (offerOpt.isEmpty()) {
            return new MobileMonthlyDiscountResponse(
                    buffetId,
                    year,
                    month,
                    List.of()
            );
        }

        MonthlyDiscountOffer offer = offerOpt.get();
        List<MonthlyDiscountOfferItem> items = itemRepository.findByOfferIdOrderByIdAsc(offer.getId());

        Long selectedOfferItemId = choiceRepository
                .findByUserIdAndBuffetIdAndYearAndMonth(userId, buffetId, year, month)
                .map(ch -> ch.getOfferItem().getId())
                .orElse(null);

        List<MobileMonthlyDiscountCategoryDto> categories = items.stream()
                .map(it -> new MobileMonthlyDiscountCategoryDto(
                        it.getId(),
                        it.getCategory().getId(),
                        it.getCategory().getName(),
                        it.getPercent() == null ? null : it.getPercent().doubleValue(),
                        selectedOfferItemId != null && selectedOfferItemId.equals(it.getId())
                ))
                .toList();

        return new MobileMonthlyDiscountResponse(
                buffetId,
                year,
                month,
                categories
        );
    }

    @Transactional
    public MobileChooseMonthlyDiscountResponse chooseCategory(Jwt jwt, Long offerItemId) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        MonthlyDiscountOffer offer = offerRepository.findByBuffetIdAndYearAndMonth(buffetId, year, month)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Для текущего буфета нет предложения скидки месяца"
                ));

        List<MonthlyDiscountOfferItem> items = itemRepository.findByOfferIdOrderByIdAsc(offer.getId());

        MonthlyDiscountOfferItem selected = items.stream()
                .filter(i -> i.getId().equals(offerItemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Выбранная категория не входит в предложение скидки месяца"
                ));

        var existingOpt = choiceRepository.findByUserIdAndBuffetIdAndYearAndMonth(userId, buffetId, year, month);
        if (existingOpt.isPresent()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("actorUserId", userId);
            data.put("buffetId", buffetId);
            data.put("year", year);
            data.put("month", month);
            data.put("existingOfferItemId", existingOpt.get().getOfferItem().getId());
            data.put("attemptOfferItemId", offerItemId);
            data.put("reason", "already_selected_this_month");

            audit.log(
                    "MOBILE_MONTHLY_DISCOUNT_SELECT_DENIED",
                    "monthly_offer_item",
                    offerItemId,
                    data
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Скидка на текущий месяц уже выбрана. Новый выбор станет доступен в следующем месяце"
            );
        }

        UserMonthlyCategoryChoice ch = new UserMonthlyCategoryChoice();
        ch.setUserId(userId);
        ch.setBuffetId(buffetId);
        ch.setYear(year);
        ch.setMonth(month);
        ch.setOfferItem(selected);

        choiceRepository.save(ch);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actorUserId", userId);
        data.put("buffetId", buffetId);
        data.put("year", year);
        data.put("month", month);
        data.put("afterOfferItemId", offerItemId);

        audit.log(
                "MOBILE_MONTHLY_DISCOUNT_SELECT_CREATE",
                "monthly_offer_item",
                offerItemId,
                data
        );

        return new MobileChooseMonthlyDiscountResponse(
                true,
                "Скидка месяца сохранена"
        );
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