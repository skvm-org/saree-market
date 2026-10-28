package com.sareemarket.booking.dto;

/** The other side of a request: the reseller for a seller, the shop for a reseller. */
public record BookingPartyDto(Long id, String name, String phone, String whatsappNumber) {
}
