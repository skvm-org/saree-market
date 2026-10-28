package com.sareemarket.booking;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.sareemarket.item.event.CollectionSoldOut;
import com.sareemarket.item.event.ItemSoldOut;

/** Runs in the seller's own transaction, so a sold out saree and its closed requests change together. */
@Component
class BookingItemListener {

	private final BookingService bookings;

	BookingItemListener(BookingService bookings) {
		this.bookings = bookings;
	}

	@EventListener
	void onItemSoldOut(ItemSoldOut event) {
		bookings.closeForSoldItem(event.itemId());
	}

	@EventListener
	void onCollectionSoldOut(CollectionSoldOut event) {
		bookings.closePendingForSoldCollection(event.collectionId());
	}

}
