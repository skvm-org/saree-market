package com.sareemarket.booking.dto;

import java.time.Instant;

import com.sareemarket.booking.BookingStatus;

/**
 * One booking request. counterparty is the reseller when a seller reads it and the shop when a reseller reads it.
 * holdExpiresAt is set only while accepted; unread is true for a reseller when the status changed since they last
 * looked (always false for a seller).
 */
public record BookingDto(Long id, BookingStatus status, String note, Instant createdAt, Instant respondedAt,
		Instant holdExpiresAt, boolean unread, BookingItemDto item, BookingPartyDto counterparty) {
}
