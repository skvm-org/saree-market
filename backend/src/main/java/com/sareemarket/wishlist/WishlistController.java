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

/**
 * A customer's saved sarees, under /api/v1/wishlist. CUSTOMER-only (class-level @PreAuthorize): other roles get 403
 * FORBIDDEN, anonymous callers 401 UNAUTHORIZED. Always acts on the caller's own list.
 */
@RestController
@RequestMapping("/api/v1/wishlist")
@PreAuthorize("hasRole('CUSTOMER')")
class WishlistController {

	private final WishlistService service;

	WishlistController(WishlistService service) {
		this.service = service;
	}

	/** GET /api/v1/wishlist. Saved sarees as catalog cards, newest saved first, 20 per page. A negative page is treated as 0. */
	@GetMapping
	PageDto<CatalogItemDto> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
		return service.list(Long.parseLong(jwt.getSubject()), page);
	}

	/**
	 * PUT /api/v1/wishlist/{itemId}. Saves a saree. Idempotent: saving one already saved still returns 204.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND (no such saree)
	 */
	@PutMapping("/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void add(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
		service.add(Long.parseLong(jwt.getSubject()), itemId);
	}

	/** DELETE /api/v1/wishlist/{itemId}. Removes a saree from the list. Returns 204 even if it was not there. */
	@DeleteMapping("/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
		service.remove(Long.parseLong(jwt.getSubject()), itemId);
	}

}
