package com.sareemarket.catalog.dto;

import java.time.Instant;

import com.sareemarket.booking.BookingStatus;
import com.sareemarket.item.ItemStatus;

public record ItemDetailDto(Long id, Long collectionId, String collectionTitle, String collectionDescription,
		String sareeType, String fabric, String colour, Integer priceOverride, int price, ItemStatus status,
		Instant createdAt, SellerContactDto seller, MyBookingDto myBooking) {

	/** The viewing reseller's latest booking request for this photo, or null if they never asked (or are not a reseller). */
	public record MyBookingDto(Long id, BookingStatus status, Instant holdExpiresAt) {
	}

	/**
	 * Phone numbers are null unless the viewer is a reseller or admin. followMode is INSTANT or MUTED when the
	 * viewing reseller follows this seller, otherwise null.
	 */
	public record SellerContactDto(Long id, String shopName, String marketLocation, String contactPhone,
			String whatsappNumber, String followMode) {
	}

}
