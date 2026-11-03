package com.sareemarket.order.dto;

/** The delivery address copied onto a hold or order, so later edits to the saved address do not change it. line2 may be null. */
public record AddressSnapshotDto(String name, String phone, String line1, String line2, String city, String state,
		String pin) {
}
