package com.sareemarket.order;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.order.dto.CheckoutRequest;
import com.sareemarket.order.dto.CheckoutResultDto;
import com.sareemarket.order.dto.HoldDto;

import jakarta.validation.Valid;

/**
 * REST endpoints for payment holds: checkout from the cart, listing the customer's open holds and cancelling one.
 * All endpoints are CUSTOMER only. Logic is in {@link HoldService}.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('CUSTOMER')")
class HoldController {

	private final HoldService holds;

	HoldController(HoldService holds) {
		this.holds = holds;
	}

	/**
	 * POST /api/v1/cart/checkout. Reserves stock for two hours, one hold per seller in the cart (or only the seller
	 * named in the request). Sellers that could not be held, and lines cut back for stock, are reported in the result
	 * rather than failing the call.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND (address, or nothing from that seller in the cart),
	 * 409 HOLD_LIMIT (more than 3 payments in progress), 409 TOTAL_TOO_LARGE
	 */
	@PostMapping("/cart/checkout")
	CheckoutResultDto checkout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckoutRequest request) {
		return holds.checkout(Long.parseLong(jwt.getSubject()), request);
	}

	/**
	 * DELETE /api/v1/holds/{id}. Cancels the customer's own hold and puts its stock back on sale. Returns 204.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND when the hold is not the customer's
	 */
	@DeleteMapping("/holds/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		holds.cancel(Long.parseLong(jwt.getSubject()), id);
	}

	/** GET /api/v1/holds. The customer's holds that have not yet expired, soonest expiry first. */
	@GetMapping("/holds")
	List<HoldDto> list(@AuthenticationPrincipal Jwt jwt) {
		return holds.list(Long.parseLong(jwt.getSubject()));
	}

}
