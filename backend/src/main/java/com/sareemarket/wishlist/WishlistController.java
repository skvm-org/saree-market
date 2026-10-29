package com.sareemarket.wishlist;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.catalog.dto.CatalogItemDto;
import com.sareemarket.common.PageDto;

@RestController
@RequestMapping("/api/v1/wishlist")
@PreAuthorize("hasRole('CUSTOMER')")
class WishlistController {

	private final WishlistService service;

	WishlistController(WishlistService service) {
		this.service = service;
	}

	@GetMapping
	PageDto<CatalogItemDto> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
		return service.list(Long.parseLong(jwt.getSubject()), page);
	}

	@PutMapping("/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void add(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
		service.add(Long.parseLong(jwt.getSubject()), itemId);
	}

	@DeleteMapping("/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
		service.remove(Long.parseLong(jwt.getSubject()), itemId);
	}

}
