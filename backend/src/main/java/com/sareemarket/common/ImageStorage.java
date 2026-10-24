package com.sareemarket.common;

import org.springframework.core.io.Resource;

/** Where uploaded photos live. Local disk now; an S3 implementation can replace it later. */
public interface ImageStorage {

	void save(String key, byte[] data);

	Resource load(String key);

	/** Deleting a missing key is not an error. */
	void delete(String key);

}
