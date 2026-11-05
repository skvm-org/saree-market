package com.sareemarket.auth;

import java.util.concurrent.TimeUnit;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.auth.dto.UserDto;

/**
 * REST endpoints for profile pictures: set, remove and view. Any logged-in role may set and remove their own; viewing
 * follows the visibility rule in {@link ProfilePhotoService}. Logic is there.
 */
@RestController
@RequestMapping("/api/v1")
class ProfilePhotoController {

	private final ProfilePhotoService photos;

	ProfilePhotoController(ProfilePhotoService photos) {
		this.photos = photos;
	}

	/**
	 * PUT /api/v1/auth/me/photo (multipart: photo). Any logged-in role. Sets or replaces the caller's picture (JPG, PNG
	 * or WebP) and returns the account with its new photoVersion.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR (no file), 400 INVALID_IMAGE
	 */
	@PutMapping(path = "/auth/me/photo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
	UserDto upload(@AuthenticationPrincipal Jwt jwt, @RequestParam(name = "photo", required = false) MultipartFile photo) {
		return photos.upload(Long.parseLong(jwt.getSubject()), photo);
	}

	/** DELETE /api/v1/auth/me/photo. Any logged-in role. Removes the caller's picture; returns 204 even if there was none. */
	@DeleteMapping("/auth/me/photo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@AuthenticationPrincipal Jwt jwt) {
		photos.remove(Long.parseLong(jwt.getSubject()));
	}

	/**
	 * GET /api/v1/users/{id}/photo[?v=photoVersion]. Any logged-in user, by the visibility rule. Streams the square JPEG.
	 * With {@code v} the response may be cached for good, because a changed picture has a new version; without it the
	 * client must check again each time.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND (no picture, or not visible to the caller)
	 */
	@GetMapping("/users/{id}/photo")
	ResponseEntity<Resource> view(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@RequestParam(name = "v", required = false) Long version) {
		Resource photo = photos.view(Long.parseLong(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString("role")), id);
		CacheControl cache = version == null ? CacheControl.noCache().cachePrivate()
				: CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate().immutable();
		return ResponseEntity.ok().cacheControl(cache).contentType(MediaType.IMAGE_JPEG).body(photo);
	}

}
