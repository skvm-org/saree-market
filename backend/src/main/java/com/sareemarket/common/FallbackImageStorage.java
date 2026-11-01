package com.sareemarket.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

/**
 * New photos go to {@code primary} (S3). A photo that is not there yet is read from {@code secondary} (the old upload
 * folder on the server's disk), so nothing breaks while earlier uploads are copied over. A failing S3 read also falls
 * back. Writes are not softened: a failing S3 save fails the upload. Deletes clear both.
 */
public class FallbackImageStorage implements ImageStorage {

	private static final Logger log = LoggerFactory.getLogger(FallbackImageStorage.class);

	private final ImageStorage primary;

	private final ImageStorage secondary;

	public FallbackImageStorage(ImageStorage primary, ImageStorage secondary) {
		this.primary = primary;
		this.secondary = secondary;
	}

	@Override
	public void save(String key, byte[] data) {
		primary.save(key, data);
	}

	@Override
	public Resource load(String key) {
		try {
			Resource resource = primary.load(key);
			if (resource.exists()) {
				return resource;
			}
		}
		catch (RuntimeException e) {
			// S3 refused or could not be reached (for example the instance role is not attached yet): older photos still show.
			log.warn("Primary storage failed for {}, reading the old folder instead: {}", key, e.toString());
		}
		return secondary.load(key);
	}

	@Override
	public void delete(String key) {
		primary.delete(key);
		secondary.delete(key);
	}

}
