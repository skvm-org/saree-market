package com.sareemarket.booking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookingStatusTest {

	@Test
	void onlyPendingAndAcceptedAreOpen() {
		assertThat(BookingStatus.values()).filteredOn(BookingStatus::isOpen)
			.containsExactlyInAnyOrder(BookingStatus.PENDING, BookingStatus.ACCEPTED);
	}

}
