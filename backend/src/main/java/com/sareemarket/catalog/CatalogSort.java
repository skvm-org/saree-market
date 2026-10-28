package com.sareemarket.catalog;

public enum CatalogSort {

	NEWEST("i.created_at DESC", "cc.created_at DESC"),
	PRICE_ASC("COALESCE(i.price_override, c.price) ASC", "cc.price ASC"),
	PRICE_DESC("COALESCE(i.price_override, c.price) DESC", "cc.price DESC");

	private final String orderBy;

	private final String collectionOrderBy;

	CatalogSort(String orderBy, String collectionOrderBy) {
		this.orderBy = orderBy;
		this.collectionOrderBy = collectionOrderBy;
	}

	/** A fixed SQL fragment from this enum, never text from the request. */
	String orderBy() {
		return orderBy;
	}

	/** The same, for the collection list, where the collection is aliased cc. */
	String collectionOrderBy() {
		return collectionOrderBy;
	}

}
