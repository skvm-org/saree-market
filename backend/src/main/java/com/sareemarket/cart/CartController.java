package com.sareemarket.cart;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.cart.dto.CartDto;
import com.sareemarket.common.ApiException;

import tools.jackson.databind.JsonNode;

/**
 * The logged-in customer's cart. Every endpoint is CUSTOMER only (class-level @PreAuthorize); sellers and admins
 * get 403. Turning the cart into orders happens in the order feature, not here.
 */
@RestController
@RequestMapping("/api/v1/cart")
@PreAuthorize("hasRole('CUSTOMER')")
class CartController {

	private final CartService service;

	CartController(CartService service) {
		this.service = service;
	}

	/**
	 * GET /api/v1/cart. The cart grouped by seller, with totals.
	 */
	@GetMapping
	CartDto view(@AuthenticationPrincipal Jwt jwt) {
		return service.view(Long.parseLong(jwt.getSubject()));
	}

	/**
	 * PUT /api/v1/cart/items/{itemId}. Adds the saree to the cart or sets its quantity (not "add this many more").
	 * The body is {"quantity": n}; answers 204.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR when quantity is missing or not a whole number
	 * 	from 1 to 10000; 404 NOT_FOUND for an unknown saree; 409 NOT_AVAILABLE when it is sold out or its seller is
	 * 	disabled; 409 NOT_ENOUGH_STOCK when quantity is above the pieces left; 409 CART_FULL when adding a new line to
	 * 	a cart that already has 50
	 */
	@PutMapping("/items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void setLine(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId, @RequestBody JsonNode body) {
		JsonNode q = body.get("quantity");
		if (q == null || !q.isIntegralNumber() || !q.canConvertToInt() || q.intValue() < 1 || q.intValue() > 10000) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid",
					Map.of("quantity", "must be a whole number from 1 to 10000"));
		}
		service.setLine(Long.parseLong(jwt.getSubject()), itemId, q.intValue());
	}

	/**
	 * DELETE /api/v1/cart/items/{itemId}. Removes the saree from the cart; answers 204 even if it was not there.
	 */
	@DeleteMapping("/items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeLine(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
		service.removeLine(Long.parseLong(jwt.getSubject()), itemId);
	}

}
