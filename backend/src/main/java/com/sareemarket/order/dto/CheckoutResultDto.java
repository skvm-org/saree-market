package com.sareemarket.order.dto;

import java.util.List;

public record CheckoutResultDto(List<HoldDto> holds, List<LineAdjustmentDto> adjustments,
		List<SkippedSellerDto> skipped) {
}
