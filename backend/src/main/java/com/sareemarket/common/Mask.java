package com.sareemarket.common;

/** Keeps personal data out of the logs: enough of a value to tell two apart, never the whole of it. */
public final class Mask {

	private Mask() {
	}

	/** "9876543210" becomes "******3210". */
	public static String last4(String value) {
		if (value == null || value.isBlank()) {
			return "-";
		}
		String v = value.strip();
		return v.length() <= 4 ? "****" : "*".repeat(v.length() - 4) + v.substring(v.length() - 4);
	}

}
