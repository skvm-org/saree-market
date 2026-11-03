package com.sareemarket.catalog;

/**
 * Which photos the catalog lists by availability. AVAILABLE is the default, SOLD_OUT lists only sold-out
 * photos, ALL applies no status condition. Filters on item.status (idx_item_status).
 */
public enum StatusFilter {
	AVAILABLE, SOLD_OUT, ALL
}
