package com.sareemarket.order.dto;

import java.time.Instant;
import java.util.List;

/** Stock is reserved for this customer until expiresAt; paying the seller's UPI ID and submitting the UTR makes it an order. */
public record HoldDto(long id, long sellerId, String shopName, String sellerUpiId, List<OrderLineDto> lines,
		int itemsTotal, int shippingFee, int grandTotal, AddressSnapshotDto address, String note, Instant expiresAt) {
}
