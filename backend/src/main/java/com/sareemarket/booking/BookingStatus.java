package com.sareemarket.booking;

import java.util.EnumSet;
import java.util.Set;

/** PENDING and ACCEPTED are open; every other status is final. */
public enum BookingStatus {
	PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED, SOLD, CLOSED;

	public static final Set<BookingStatus> OPEN = EnumSet.of(PENDING, ACCEPTED);

	public boolean isOpen() {
		return OPEN.contains(this);
	}
}
