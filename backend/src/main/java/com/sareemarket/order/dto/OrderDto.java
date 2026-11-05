package com.sareemarket.order.dto;

import java.time.Instant;
import java.util.List;

import com.sareemarket.order.OrderStatus;

/**
 * One order as the viewing customer or seller sees it. customer is set only in the seller's view; unread is the
 * viewer's own flag; history is filled in the detail view and empty in lists (steps of the earlier flow are left
 * out). courier and trackingNumber are set from SHIPPED on.
 */
public record OrderDto(long id, OrderStatus status, long sellerId, String shopName, String sellerUpiId,
		OrderPartyDto customer, List<OrderLineDto> lines, int itemsTotal, int shippingFee, int grandTotal,
		AddressSnapshotDto address, String note, String paymentReference, boolean hasProof, String cancelReason,
		String cancelledBy, String courier, String trackingNumber,
		boolean unread, Instant createdAt, Instant updatedAt, List<StatusEntryDto> history) {
}
