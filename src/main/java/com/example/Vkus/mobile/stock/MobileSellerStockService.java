package com.example.Vkus.mobile.stock;

import com.example.Vkus.entity.Buffet;
import com.example.Vkus.mobile.stock.dto.MobileSellerStockRowDto;
import com.example.Vkus.mobile.stock.dto.MobileSellerStocksResponse;
import com.example.Vkus.mobile.stock.dto.MobileSellerWarehouseMailRequest;
import com.example.Vkus.mobile.stock.dto.MobileSellerWarehouseMailResponse;
import com.example.Vkus.mobile.stock.dto.MobileWarehouseUserDto;
import com.example.Vkus.repository.BuffetRepository;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.MailService;
import com.example.Vkus.service.SellerStockService;
import com.example.Vkus.service.WarehouseUserLookupService;
import com.example.Vkus.web.dto.SellerProductStockRow;
import com.example.Vkus.web.dto.WarehouseUserOption;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MobileSellerStockService {

    private final SellerStockService sellerStockService;
    private final WarehouseUserLookupService warehouseUserLookupService;
    private final MailService mailService;
    private final AuditLogService audit;
    private final BuffetRepository buffetRepository;

    public MobileSellerStockService(SellerStockService sellerStockService,
                                    WarehouseUserLookupService warehouseUserLookupService,
                                    MailService mailService,
                                    AuditLogService audit,
                                    BuffetRepository buffetRepository) {
        this.sellerStockService = sellerStockService;
        this.warehouseUserLookupService = warehouseUserLookupService;
        this.mailService = mailService;
        this.audit = audit;
        this.buffetRepository = buffetRepository;
    }

    @Transactional(readOnly = true)
    public MobileSellerStocksResponse getStocks(Jwt jwt) {
        Long buffetId = extractRequiredBuffetId(jwt);
        ensureSellerAccess(jwt);

        Buffet buffet = buffetRepository.findById(buffetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Буфет не найден"));

        List<MobileSellerStockRowDto> rows = sellerStockService.getStocks(buffetId).stream()
                .map(this::toMobileRow)
                .toList();

        List<MobileWarehouseUserDto> warehouseUsers = warehouseUserLookupService.getWarehouseUsers().stream()
                .map(u -> new MobileWarehouseUserDto(
                        u.id(),
                        u.fullName(),
                        u.email(),
                        u.label()
                ))
                .toList();

        String buffetName = buffet.getName();

        String defaultSubject = "Запрос закупки и доставки для буфета \"" + buffetName + "\"";
        String defaultBody =
                "Здравствуйте!\n\n" +
                        "Прошу закупить и доставить товары для буфета \"" + buffetName + "\".\n" +
                        "Ниже перечисляю необходимые позиции:\n" +
                        "1.\n" +
                        "2.\n" +
                        "3.\n\n" +
                        "Спасибо!";

        return new MobileSellerStocksResponse(
                buffetId,
                buffetName,
                rows,
                warehouseUsers,
                defaultSubject,
                defaultBody
        );
    }

    @Transactional
    public MobileSellerWarehouseMailResponse sendWarehouseRequest(Jwt jwt, MobileSellerWarehouseMailRequest request) {
        Long actorUserId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractRequiredBuffetId(jwt);
        ensureSellerAccess(jwt);

        List<WarehouseUserOption> warehouseUsers = warehouseUserLookupService.getWarehouseUsers();

        WarehouseUserOption target = warehouseUsers.stream()
                .filter(u -> u.id().equals(request.warehouseUserId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сотрудник склада не найден"));

        String warehouseEmail = target.email();
        String sellerEmail = extractOptionalString(jwt.getClaims().get("email"));

        mailService.sendPlainText(warehouseEmail, request.subject(), request.body());

        if (sellerEmail != null && !sellerEmail.isBlank() && !sellerEmail.equalsIgnoreCase(warehouseEmail)) {
            mailService.sendPlainText(sellerEmail, request.subject(), request.body());
        }

        audit.logAs(actorUserId, "MOBILE_WAREHOUSE_REQUEST_EMAIL", "buffet", buffetId, Map.of(
                "mail", snapshotMail(request, target, buffetId, actorUserId, sellerEmail)
        ));

        return new MobileSellerWarehouseMailResponse(
                true,
                "Письмо отправлено: " + target.label()
        );
    }

    private MobileSellerStockRowDto toMobileRow(SellerProductStockRow row) {
        return new MobileSellerStockRowDto(
                row.productId(),
                row.name(),
                row.basePrice() == null ? 0.0 : row.basePrice().doubleValue(),
                row.qty(),
                row.qty() != null && row.qty() > 0
        );
    }

    private Map<String, Object> snapshotMail(MobileSellerWarehouseMailRequest request,
                                             WarehouseUserOption target,
                                             Long buffetId,
                                             Long actorUserId,
                                             String sellerEmail) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("buffetId", buffetId);
        meta.put("actorUserId", actorUserId);
        meta.put("warehouseUserId", request.warehouseUserId());
        meta.put("warehouseEmail", target.email());
        meta.put("sellerEmail", sellerEmail);
        meta.put("subject", cut(request.subject(), 200));
        meta.put("body", cut(request.body(), 2000));
        return meta;
    }

    private String cut(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private void ensureSellerAccess(Jwt jwt) {
        List<String> roles = extractRoles(jwt.getClaims().get("roles"));
        boolean allowed = roles.stream().anyMatch(r ->
                "seller".equalsIgnoreCase(r) || "buffet_admin".equalsIgnoreCase(r)
        );

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к остаткам продавца");
        }
    }

    private List<String> extractRoles(Object value) {
        if (value instanceof java.util.Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Long extractLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) return Long.parseLong(s);

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Некорректный uid в токене");
    }

    private Long extractRequiredBuffetId(Jwt jwt) {
        Object value = jwt.getClaims().get("defaultBuffetId");
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) return Long.parseLong(s);

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
    }

    private String extractOptionalString(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isBlank() ? null : s;
    }
}