package com.sareemarket.booking.dto;

/**
 * Counts for the badges and the seller's tracking tiles. A seller gets received, pending, onHold, responded, sold,
 * rejected and expired; a reseller gets unread and open. The fields that do not apply to the caller are 0.
 * responded counts requests the seller accepted, rejected or sold.
 */
public record BookingSummaryDto(long received, long pending, long onHold, long responded, long sold, long rejected,
		long expired, long unread, long open) {
}
