package com.sareemarket.catalog;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Query parameters of GET /catalog, GET /catalog/collections and the image search. Everything is optional; absent
 * means "do not filter on this". Defaults filled in by the constructor: status AVAILABLE, sort NEWEST, page 0.
 * <ul>
 * <li>q: free text, up to 200 characters. Each word is matched as a prefix against the photo's search vector
 * (collection title, description and colour), using its GIN index idx_item_search.</li>
 * <li>colour: case-insensitive "contains" match on the photo's colour, up to 50 characters.</li>
 * <li>minPrice, maxPrice: whole rupees, compared with the saree's own price if set, else the collection's.</li>
 * <li>sellerId: only this seller's photos.</li>
 * <li>status: availability, see StatusFilter.</li>
 * <li>since: only photos uploaded within this many days (1 to 3650).</li>
 * <li>sort: see CatalogSort.</li>
 * <li>page: zero-based; a page holds CatalogService.PAGE_SIZE results.</li>
 * <li>following=true: only sellers the logged-in reseller follows.</li>
 * </ul>
 */
public record CatalogFilter(@Size(max = 200) String q,
		@Size(max = 50) String colour, @Min(0) Integer minPrice, @Min(0) Integer maxPrice, Long sellerId,
		StatusFilter status, @Min(1) @Max(3650) Integer since, CatalogSort sort, @Min(0) Integer page, Boolean following) {

	public CatalogFilter {
		status = status == null ? StatusFilter.AVAILABLE : status;
		sort = sort == null ? CatalogSort.NEWEST : sort;
		page = page == null ? 0 : page;
	}

}
