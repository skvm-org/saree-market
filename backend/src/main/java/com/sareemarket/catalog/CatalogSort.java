package com.sareemarket.catalog;

public enum CatalogSort {

	NEWEST("i.created_at DESC"),
	PRICE_ASC("COALESCE(i.price_override, c.price) ASC"),
	PRICE_DESC("COALESCE(i.price_override, c.price) DESC");

	private final String orderBy;

	CatalogSort(String orderBy) {
		this.orderBy = orderBy;
	}

	/** A fixed SQL fragment from this enum, never text from the request. */
	String orderBy() {
		return orderBy;
	}

}
