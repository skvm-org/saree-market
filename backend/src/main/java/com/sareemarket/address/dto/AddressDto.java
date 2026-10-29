package com.sareemarket.address.dto;

public record AddressDto(Long id, String name, String phone, String line1, String line2, String city, String state,
		String pin, boolean isDefault) {
}
