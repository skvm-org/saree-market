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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.order.dto.OrderDto;

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

	@GetMapping("/orders/{id}/proof")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'SELLER')")
	ResponseEntity<Resource> proof(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
			.contentType(MediaType.IMAGE_JPEG)
			.body(orders.proof(userId(jwt), id));
	}

	static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
