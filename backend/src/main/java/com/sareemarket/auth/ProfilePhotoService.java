package com.sareemarket.auth;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.auth.dto.UserDto;
import com.sareemarket.common.ApiException;
import com.sareemarket.common.ImageStorage;
import com.sareemarket.item.ImageProcessor;

/**
 * Profile pictures: a user sets, replaces and removes their own, and others view it by the visibility rule: a seller's
 * is visible to every logged-in user; a customer's only to that customer, to an admin, and to a seller who has an order
 * from them. A replaced or removed picture's file is deleted once the change has committed, and a new file is deleted
 * again if the change rolls back, so the database and the storage do not drift apart.
 */
@Service
public class ProfilePhotoService {

	private static final Logger log = LoggerFactory.getLogger(ProfilePhotoService.class);

	private final UserRepository users;

	private final ImageProcessor images;

	private final ImageStorage storage;

	private final NamedParameterJdbcTemplate jdbc;

	ProfilePhotoService(UserRepository users, ImageProcessor images, ImageStorage storage, NamedParameterJdbcTemplate jdbc) {
		this.users = users;
		this.images = images;
		this.storage = storage;
		this.jdbc = jdbc;
	}

	/**
	 * Sets or replaces the caller's picture: validated, centre-cropped to a square and stored as a 512 px JPEG.
	 *
	 * @return the caller's account with the new photoVersion
	 * @throws ApiException 400 VALIDATION_ERROR (no file), 400 INVALID_IMAGE (not a JPG, PNG or WebP photo),
	 * 401 UNAUTHORIZED (the account no longer exists)
	 */
	@Transactional
	public UserDto upload(long userId, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid",
					java.util.Map.of("photo", "choose a photo"));
		}
		byte[] jpeg = images.avatar(file);
		AppUser user = findUser(userId);
		String key = "avatars/" + userId + "-" + UUID.randomUUID() + ".jpg";
		storage.save(key, jpeg);
		String old = user.getPhotoKey();
		// Whole milliseconds, so the version a client gets here equals the one the database computes for other users' views.
		user.setPhoto(key, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
		afterCommitDelete(old, key);
		return UserDto.from(user);
	}

	/** Removes the caller's picture; having none is fine. */
	@Transactional
	public UserDto remove(long userId) {
		AppUser user = findUser(userId);
		String old = user.getPhotoKey();
		user.setPhoto(null, null);
		afterCommitDelete(old, null);
		return UserDto.from(user);
	}

	/**
	 * The picture of {@code targetId} for a viewer, if the viewer may see it.
	 *
	 * @throws ApiException 404 NOT_FOUND when there is no picture, the file is gone, or the viewer may not see it (the
	 * cases look the same, so a customer's picture is not revealed to someone who may not see it)
	 */
	public Resource view(long viewerId, Role viewerRole, long targetId) {
		AppUser target = users.findById(targetId).orElseThrow(ProfilePhotoService::notFound);
		if (target.getPhotoKey() == null || !canView(viewerId, viewerRole, target)) {
			throw notFound();
		}
		Resource resource = storage.load(target.getPhotoKey());
		if (!resource.exists()) {
			throw notFound();
		}
		return resource;
	}

	private boolean canView(long viewerId, Role viewerRole, AppUser target) {
		if (viewerId == target.getId() || viewerRole == Role.ADMIN || target.getRole() == Role.SELLER) {
			return true;
		}
		// A customer's picture: only a seller who has an order from them.
		return viewerRole == Role.SELLER && Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM customer_order WHERE seller_id = :s AND customer_id = :c)",
				new MapSqlParameterSource("s", viewerId).addValue("c", target.getId()), Boolean.class));
	}

	/** Deletes the replaced file after commit, and the file just written if the transaction rolls back. */
	private void afterCommitDelete(String oldKey, String newKey) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				String stale = status == STATUS_COMMITTED ? oldKey : newKey;
				if (stale == null) {
					return;
				}
				try {
					storage.delete(stale);
				}
				catch (RuntimeException e) {
					log.warn("Could not delete profile picture file {}", stale, e);
				}
			}
		});
	}

	private AppUser findUser(long userId) {
		return users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Login required"));
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Photo not found");
	}

}
