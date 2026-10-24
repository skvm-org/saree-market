package com.sareemarket.common;

import java.util.List;

public record PageDto<T>(List<T> items, int page, int pageSize, long totalItems, int totalPages) {

	public static <T> PageDto<T> of(List<T> items, int page, int pageSize, long totalItems) {
		return new PageDto<>(items, page, pageSize, totalItems, (int) ((totalItems + pageSize - 1) / pageSize));
	}

}
