package com.sareemarket.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FallbackImageStorageTest {

	private ImageStorage primary;

	private ImageStorage secondary;

	private FallbackImageStorage storage;

	@BeforeEach
	void setUp() throws IOException {
		primary = new LocalDiskImageStorage(Files.createTempDirectory("saree-primary"));
		secondary = new LocalDiskImageStorage(Files.createTempDirectory("saree-secondary"));
		storage = new FallbackImageStorage(primary, secondary);
	}

	@Test
	void newPhotosGoOnlyToThePrimary() {
		storage.save("1/new.jpg", new byte[] { 1 });

		assertThat(primary.load("1/new.jpg").exists()).isTrue();
		assertThat(secondary.load("1/new.jpg").exists()).isFalse();
	}

	@Test
	void aPhotoNotInThePrimaryIsReadFromTheOldFolder() throws IOException {
		secondary.save("1/old.jpg", new byte[] { 4, 5 });

		assertThat(storage.load("1/old.jpg").getContentAsByteArray()).containsExactly(4, 5);
	}

	@Test
	void thePrimaryWinsWhenBothHaveThePhoto() throws IOException {
		primary.save("1/both.jpg", new byte[] { 1 });
		secondary.save("1/both.jpg", new byte[] { 2 });

		assertThat(storage.load("1/both.jpg").getContentAsByteArray()).containsExactly(1);
	}

	@Test
	void aFailingPrimaryStillServesTheOldFolder() throws IOException {
		secondary.save("1/old.jpg", new byte[] { 6 });
		ImageStorage broken = new ImageStorage() {
			@Override
			public void save(String key, byte[] data) {
				throw new IllegalStateException("denied");
			}

			@Override
			public org.springframework.core.io.Resource load(String key) {
				throw new IllegalStateException("denied");
			}

			@Override
			public void delete(String key) {
			}
		};

		assertThat(new FallbackImageStorage(broken, secondary).load("1/old.jpg").getContentAsByteArray()).containsExactly(6);
	}

	@Test
	void aPhotoInNeitherDoesNotExist() {
		assertThat(storage.load("1/none.jpg").exists()).isFalse();
	}

	@Test
	void deletingClearsBothSoAnOldCopyCannotReappear() {
		primary.save("1/x.jpg", new byte[] { 1 });
		secondary.save("1/x.jpg", new byte[] { 2 });

		storage.delete("1/x.jpg");

		assertThat(storage.load("1/x.jpg").exists()).isFalse();
	}

}
