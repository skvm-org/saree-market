package com.sareemarket.catalog;

/**
 * How catalog results are ordered. Each value carries two fixed SQL fragments (never request text): one for the
 * photo grid, where the photo is aliased i and its collection c, and one for the collection list, where the
 * collection is aliased cc. Price sorts use the saree's own price if set, else the collection's. The default
 * NEWEST sort on available photos can use idx_item_available_newest (created_at DESC, AVAILABLE rows only).
 */
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
