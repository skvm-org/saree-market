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

@RestController
@RequestMapping("/api/v1")
class OrderController {

	private final OrderService orders;

	OrderController(OrderService orders) {
		this.orders = orders;
	}

	@PostMapping(path = "/holds/{id}/payment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('CUSTOMER')")
	@ResponseStatus(HttpStatus.CREATED)
	OrderDto pay(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@RequestParam(name = "utr", required = false) String utr,
			@RequestParam(name = "proof", required = false) MultipartFile proof) {
		return orders.submitPayment(userId(jwt), id, utr, proof);
	}

	@GetMapping("/orders/mine")
	@PreAuthorize("hasRole('CUSTOMER')")
	PageDto<OrderDto> mine(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) OrderStatus state,
			@RequestParam(defaultValue = "0") int page) {
		return orders.mine(userId(jwt), state, page);
	}

	@GetMapping("/orders/summary")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	OrderSummaryDto summary(@AuthenticationPrincipal Jwt jwt) {
		return orders.summary(userId(jwt), isSeller(jwt));
	}

	@PostMapping("/orders/seen")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void seen(@AuthenticationPrincipal Jwt jwt) {
		orders.markSeen(userId(jwt), isSeller(jwt));
	}

	@GetMapping("/orders/{id}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	OrderDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return orders.get(userId(jwt), isSeller(jwt), id);
	}

	@GetMapping("/orders/received")
	@PreAuthorize("hasRole('SELLER')")
	PageDto<OrderDto> received(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) OrderStatus state,
			@RequestParam(defaultValue = "0") int page) {
		return orders.received(userId(jwt), state, page);
	}

	@PostMapping("/orders/{id}/cancel")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	OrderDto cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@RequestBody(required = false) CancelRequest body) {
		if (isSeller(jwt)) {
			return orders.cancelBySeller(userId(jwt), id, body == null ? null : body.reason());
		}
		return orders.cancelByCustomer(userId(jwt), id);
	}

	@PostMapping(path = "/orders/{id}/payment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('CUSTOMER')")
	OrderDto repay(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@RequestParam(name = "utr", required = false) String utr,
			@RequestParam(name = "proof", required = false) MultipartFile proof) {
		return orders.resubmitPayment(userId(jwt), id, utr, proof);
	}

	@PostMapping("/orders/{id}/confirm-payment")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return orders.confirmPayment(userId(jwt), id);
	}

	@PostMapping("/orders/{id}/reject-payment")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody RejectRequest body) {
		return orders.rejectPayment(userId(jwt), id, body.reason());
	}

	@PostMapping("/orders/{id}/pack")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto pack(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return orders.pack(userId(jwt), id);
	}

	@PostMapping("/orders/{id}/ship")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto ship(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@Valid @RequestBody(required = false) ShipRequest body) {
		return orders.ship(userId(jwt), id, body == null ? null : body.courier(), body == null ? null : body.trackingNumber());
	}

	@PostMapping("/orders/{id}/deliver")
	@PreAuthorize("hasRole('SELLER')")
	OrderDto deliver(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return orders.deliver(userId(jwt), id);
	}

	@GetMapping("/orders/{id}/proof")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	ResponseEntity<Resource> proof(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
			.contentType(MediaType.IMAGE_JPEG)
			.body(orders.proof(userId(jwt), id));
	}

	static boolean isSeller(Jwt jwt) {
		return "SELLER".equals(jwt.getClaimAsString("role"));
	}

	static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
