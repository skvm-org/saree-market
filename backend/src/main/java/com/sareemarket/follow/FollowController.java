package com.sareemarket.follow;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.follow.dto.FollowDto;
import com.sareemarket.follow.dto.FollowRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/follows")
@PreAuthorize("hasRole('CUSTOMER')")
class FollowController {

	private final FollowService service;

	FollowController(FollowService service) {
		this.service = service;
	}

	@GetMapping
	List<FollowDto> list(@AuthenticationPrincipal Jwt jwt) {
		return service.list(Long.parseLong(jwt.getSubject()));
	}

	@PutMapping("/{sellerId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void follow(@AuthenticationPrincipal Jwt jwt, @PathVariable long sellerId, @Valid @RequestBody FollowRequest request) {
		service.follow(Long.parseLong(jwt.getSubject()), sellerId, request.notifyMode());
	}

	@DeleteMapping("/{sellerId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable long sellerId) {
		service.unfollow(Long.parseLong(jwt.getSubject()), sellerId);
	}

}
