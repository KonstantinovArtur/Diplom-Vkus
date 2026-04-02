package com.example.Vkus.mobile.combo.dto;

import java.util.List;

public record MobileComboSlotDto(
        Long slotId,
        String name,
        Integer requiredQty,
        Integer sortOrder,
        List<MobileComboSlotOptionDto> options
) {
}