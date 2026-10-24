package com.sareemarket.catalog;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Query parameters of GET /catalog. Everything is optional; absent means "do not filter on this". */
public record CatalogFilter(@Size(max = 200) String q, List<Long> typeIds, List<Long> fabricIds,
		@Size(max = 50) String colour, @Min(0) Integer minPrice, @Min(0) Integer maxPrice, Long sellerId,
		StatusFilter status, @Min(1) Integer since, CatalogSort sort, @Min(0) Integer page) {

	public CatalogFilter {
		status = status == null ? StatusFilter.AVAILABLE : status;
		sort = sort == null ? CatalogSort.NEWEST : sort;
		page = page == null ? 0 : page;
	}

}
