package com.example.Vkus.mobile.combo.dto;

import java.util.List;

public record MobileComboListResponse(
        Long buffetId,
        List<MobileComboSummaryDto> combos
) {
}