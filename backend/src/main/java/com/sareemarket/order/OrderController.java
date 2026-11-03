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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import com.sareemarket.common.PageDto;
import com.sareemarket.order.dto.CancelRequest;
import com.sareemarket.order.dto.OrderDto;
import com.sareemarket.order.dto.RejectRequest;
import com.sareemarket.order.dto.ShipRequest;
import com.sareemarket.order.dto.OrderSummaryDto;

/**
 * REST endpoints for orders after payment proof is submitted: the customer's and seller's order lists, detail, the
 * seller's confirm/reject/pack/ship/deliver steps, cancel, payment resubmission and the proof image. The caller's id
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
	 * payment hold into an order awaiting the seller's confirmation.
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
	 * GET /api/v1/orders/received?state=&page=. SELLER only. Orders placed with this seller; those waiting for payment
	 * confirmation come first, then newest first.
	 */
	@GetMapping("/orders/received")
	@PreAuthorize("hasRole('SELLER')")
	PageDto<OrderDto> received(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) OrderStatus state,
			@RequestParam(defaultValue = "0") int page) {
		return orders.received(userId(jwt), state, page);
	}

	/**
	 * POST /api/v1/orders/{id}/cancel (optional body with reason). CUSTOMER or SELLER. A customer can cancel while the
	 * payment is submitted or rejected; a seller can cancel until the order ships and must give a reason.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR (seller without a reason), 404 NOT_FOUND,
	 * 409 BAD_STATE
	 */
	@PostMapping("/orders/{id}/cancel")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	OrderDto cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@Valid @RequestBody(required = false) CancelRequest body) {
		if (isSeller(jwt)) {
			return orders.cancelBySeller(userId(jwt), id, body == null ? null : body.reason());
		}
		return orders.cancelByCustomer(userId(jwt), id);
	}

	/**
	 * POST /api/v1/orders/{id}/payment (multipart: utr, optional proof image). CUSTOMER only. Sends a corrected UTR for
	 * an order whose payment the seller rejected; the order goes back to the seller for confirmation.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR (UTR not 12 digits), 404 NOT_FOUND,
	 * 409 BAD_STATE (not rejected), 409 HOLD_EXPIRED (two hours passed), 409 UTR_IN_USE
	 */
	@PostMapping(path = "/orders/{id}/payment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('CUSTOMER')")
	OrderDto repay(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@RequestParam(name = "utr", required = false) String utr,
			@RequestParam(name = "proof", required = false) MultipartFile proof) {
		return orders.resubmitPayment(userId(jwt), id, utr, proof);
	}

	/**
	 * POST /api/v1/orders/{id}/confirm-payment. SELLER only. The seller found the payment: PAYMENT_SUBMITTED to
	 * CONFIRMED.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND, 409 BAD_STATE
	 */
	@PostMapping("/orders/{id}/confirm-payment")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return orders.confirmPayment(userId(jwt), id);
	}

	/**
	 * POST /api/v1/orders/{id}/reject-payment (body: reason). SELLER only. The first rejection gives the customer two
	 * hours to resubmit; a second one cancels the order.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR (blank reason), 404 NOT_FOUND, 409 BAD_STATE
	 */
	@PostMapping("/orders/{id}/reject-payment")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody RejectRequest body) {
		return orders.rejectPayment(userId(jwt), id, body.reason());
	}

	/**
	 * POST /api/v1/orders/{id}/pack. SELLER only. CONFIRMED to PACKED.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND, 409 BAD_STATE
	 */
	@PostMapping("/orders/{id}/pack")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto pack(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return orders.pack(userId(jwt), id);
	}

	/**
	 * POST /api/v1/orders/{id}/ship (optional body with courier and trackingNumber). SELLER only. PACKED to SHIPPED.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND, 409 BAD_STATE
	 */
	@PostMapping("/orders/{id}/ship")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto ship(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@Valid @RequestBody(required = false) ShipRequest body) {
		return orders.ship(userId(jwt), id, body == null ? null : body.courier(), body == null ? null : body.trackingNumber());
	}

	/**
	 * POST /api/v1/orders/{id}/deliver. SELLER only. SHIPPED to DELIVERED.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND, 409 BAD_STATE
	 */
	@PostMapping("/orders/{id}/deliver")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto deliver(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return orders.deliver(userId(jwt), id);
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
