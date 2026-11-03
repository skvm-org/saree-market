package com.sareemarket.order.dto;

import java.util.List;

/**
 * What checkout did. holds: the payments now open, one per seller. adjustments: lines reserved in a smaller quantity
 * than wanted. skipped: sellers that got no hold, with the reason.
 */
public record CheckoutResultDto(List<HoldDto> holds, List<LineAdjustmentDto> adjustments,
		List<SkippedSellerDto> skipped) {
}
