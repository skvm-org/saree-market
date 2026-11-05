package com.sareemarket.order;

import java.util.concurrent.TimeUnit;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import com.sareemarket.common.PageDto;
import com.sareemarket.order.dto.CancelRequest;
import com.sareemarket.order.dto.OrderDto;
import com.sareemarket.order.dto.ShipRequest;
import com.sareemarket.order.dto.OrderSummaryDto;

/**
 * REST endpoints for orders after payment proof is submitted: the customer's and seller's order lists, detail, the
 * seller's ship and tracking steps, the customer's receive, cancel and the proof image. The caller's id
 * and role come from the JWT; an order that belongs to someone else is always a 404 NOT_FOUND. Logic is in
 * {@link OrderService}.
 */
@RestController
@RequestMapping("/api/v1")
class OrderController {

	private final OrderService orders;

	OrderController(OrderService orders) {
		this.orders = orders;
	}

	/**
	 * POST /api/v1/holds/{id}/payment (multipart: utr, optional proof image). CUSTOMER only. Turns the customer's
	 * payment hold into a CONFIRMED order.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR (UTR not 12 digits), 404 NOT_FOUND (not the
	 * customer's hold), 409 HOLD_EXPIRED, 409 UTR_IN_USE
	 */
	@PostMapping(path = "/holds/{id}/payment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('CUSTOMER')")
	@ResponseStatus(HttpStatus.CREATED)
	OrderDto pay(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@RequestParam(name = "utr", required = false) String utr,
			@RequestParam(name = "proof", required = false) MultipartFile proof) {
		return orders.submitPayment(userId(jwt), id, utr, proof);
	}

	/**
	 * GET /api/v1/orders/mine?state=&page=. CUSTOMER only. The customer's own orders, newest first, 20 per page,
	 * optionally filtered by status.
	 */
	@GetMapping("/orders/mine")
	@PreAuthorize("hasRole('CUSTOMER')")
	PageDto<OrderDto> mine(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) OrderStatus state,
			@RequestParam(defaultValue = "0") int page) {
		return orders.mine(userId(jwt), state, page);
	}

	/**
	 * GET /api/v1/orders/summary. CUSTOMER or SELLER. Counts for the app badge and background alerts; the fields
	 * depend on the caller's role (see {@link OrderSummaryDto}).
	 */
	@GetMapping("/orders/summary")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	OrderSummaryDto summary(@AuthenticationPrincipal Jwt jwt) {
		return orders.summary(userId(jwt), isSeller(jwt));
	}

	/** POST /api/v1/orders/seen. CUSTOMER or SELLER. Clears the caller's unread flag on all their orders. Returns 204. */
	@PostMapping("/orders/seen")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void seen(@AuthenticationPrincipal Jwt jwt) {
		orders.markSeen(userId(jwt), isSeller(jwt));
	}

	/**
	 * GET /api/v1/orders/{id}. CUSTOMER or SELLER. One order with its status history, as the caller sees it.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND when the order is not the caller's
	 */
	@GetMapping("/orders/{id}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	OrderDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return orders.get(userId(jwt), isSeller(jwt), id);
	}

	/**
	 * GET /api/v1/orders/received?state=&page=. SELLER only. Orders placed with this seller; those still to ship (confirmed)
	 * come first, then newest first.
	 */
	@GetMapping("/orders/received")
	@PreAuthorize("hasRole('SELLER')")
	PageDto<OrderDto> received(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) OrderStatus state,
			@RequestParam(defaultValue = "0") int page) {
		return orders.received(userId(jwt), state, page);
	}

	/**
	 * POST /api/v1/orders/{id}/cancel (optional body with reason). CUSTOMER or SELLER. A customer can cancel while the
	 * order is CONFIRMED (the reason is optional); a seller can cancel at the same point and must give a reason.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR (seller without a reason), 404 NOT_FOUND,
	 * 409 BAD_STATE (already shipped, delivered or cancelled)
	 */
	@PostMapping("/orders/{id}/cancel")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	OrderDto cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@Valid @RequestBody(required = false) CancelRequest body) {
		if (isSeller(jwt)) {
			return orders.cancelBySeller(userId(jwt), id, body == null ? null : body.reason());
		}
		return orders.cancelByCustomer(userId(jwt), id, body == null ? null : body.reason());
	}

	/**
	 * POST /api/v1/orders/{id}/ship (body: courier, trackingNumber, both required). SELLER only. CONFIRMED to SHIPPED;
	 * the customer is notified with the tracking number.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR (blank courier or tracking number),
	 * 404 NOT_FOUND, 409 BAD_STATE
	 */
	@PostMapping("/orders/{id}/ship")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto ship(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody ShipRequest body) {
		return orders.ship(userId(jwt), id, body.courier(), body.trackingNumber());
	}

	/**
	 * PUT /api/v1/orders/{id}/tracking (body: courier, trackingNumber, both required). SELLER only. Corrects the
	 * tracking of a shipped order; the customer is notified again.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR, 404 NOT_FOUND, 409 BAD_STATE (not shipped)
	 */
	@PutMapping("/orders/{id}/tracking")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto tracking(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody ShipRequest body) {
		return orders.updateTracking(userId(jwt), id, body.courier(), body.trackingNumber());
	}

	/**
	 * POST /api/v1/orders/{id}/receive. CUSTOMER only. The customer got the parcel: SHIPPED to DELIVERED.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND, 409 BAD_STATE (not shipped)
	 */
	@PostMapping("/orders/{id}/receive")
	@PreAuthorize("hasRole('CUSTOMER')")
	OrderDto receive(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return orders.receive(userId(jwt), id);
	}

	/**
	 * GET /api/v1/orders/{id}/proof. CUSTOMER or SELLER of that order. The payment screenshot as a JPEG, cached
	 * privately for an hour.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND when the order is not the caller's or has no proof
	 */
	@GetMapping("/orders/{id}/proof")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	ResponseEntity<Resource> proof(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
			.contentType(MediaType.IMAGE_JPEG)
			.body(orders.proof(userId(jwt), id));
	}

	/** True when the token's role claim is SELLER; the other allowed role here is CUSTOMER. */
	static boolean isSeller(Jwt jwt) {
		return "SELLER".equals(jwt.getClaimAsString("role"));
	}

	/** The caller's user id, which is the token subject. */
	static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
