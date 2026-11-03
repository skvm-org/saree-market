package com.sareemarket.common;

import org.springframework.core.io.Resource;

/**
 * Where uploaded photos live: collection photos, thumbnails and payment proofs. Services save and delete through this
 * interface and the image endpoints stream from it. Implementations are {@link LocalDiskImageStorage} (default),
 * {@link S3ImageStorage}, and {@link FallbackImageStorage} wrapping both; {@code StorageConfig} picks one. Keys are
 * relative paths made by the server, such as {@code 12/uuid.jpg}.
 */
public interface ImageStorage {

	/** Stores the bytes under the key, replacing any earlier file with that key. */
	void save(String key, byte[] data);

	/** Returns the stored file. A missing key gives a Resource whose {@code exists()} is false, not an exception. */
	Resource load(String key);

	/** Deleting a missing key is not an error. */
	void delete(String key);

}
