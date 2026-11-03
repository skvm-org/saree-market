package com.sareemarket.address.dto;

/**
 * A saved address as returned to its owner.
 *
 * @param line2 null when not given
 * @param pin 6-digit PIN code, kept as text
 * @param isDefault true for the customer's default address
 */
public record AddressDto(Long id, String name, String phone, String line1, String line2, String city, String state,
		String pin, boolean isDefault) {
}
