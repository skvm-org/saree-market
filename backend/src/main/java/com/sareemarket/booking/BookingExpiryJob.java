package com.sareemarket.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases holds nobody completed and drops requests the seller never answered. Scheduling is enabled in AsyncConfig. */
@Component
class BookingExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

	private final BookingService bookings;

	BookingExpiryJob(BookingService bookings) {
		this.bookings = bookings;
	}

	@Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
	void run() {
		try {
			bookings.expireOverdue();
		}
		catch (RuntimeException e) {
			log.warn("Booking expiry run failed; it will try again in an hour", e);
		}
	}

}
