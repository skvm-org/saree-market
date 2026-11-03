package com.sareemarket.common;

import java.util.List;

/**
 * One page of a list, in the same shape for every list endpoint.
 *
 * @param page zero-based page number, as the client asked for it
 * @param pageSize maximum items per page
 * @param totalItems count of matching rows across all pages
 * @param totalPages number of pages needed for totalItems; 0 when there are no items
 */
public record PageDto<T>(List<T> items, int page, int pageSize, long totalItems, int totalPages) {

	/** Builds a page and works out totalPages by rounding totalItems / pageSize up. */
	public static <T> PageDto<T> of(List<T> items, int page, int pageSize, long totalItems) {
		return new PageDto<>(items, page, pageSize, totalItems, (int) ((totalItems + pageSize - 1) / pageSize));
	}

}
