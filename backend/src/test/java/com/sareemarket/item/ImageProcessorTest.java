package com.sareemarket.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.sareemarket.TestImages;
import com.sareemarket.common.ApiException;

class ImageProcessorTest {

	// A 1x1 lossless WebP.
	private static final String TINY_WEBP = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";

	private final ImageProcessor processor = new ImageProcessor();

	private static MockMultipartFile file(String name, byte[] bytes) {
		return new MockMultipartFile("files", name, "image/jpeg", bytes);
	}

	private static BufferedImage decode(byte[] jpeg) throws Exception {
		return ImageIO.read(new ByteArrayInputStream(jpeg));
	}

	@Test
	void bigPhotoIsShrunkToFitInside1600AndThumbnailInside400() throws Exception {
		ProcessedImage result = processor.process(file("big.jpg", TestImages.jpeg(3000, 2000, 1)));

		BufferedImage full = decode(result.full());
		BufferedImage thumb = decode(result.thumb());
		assertThat(Math.max(full.getWidth(), full.getHeight())).isEqualTo(1600);
		assertThat(full.getWidth()).isGreaterThan(full.getHeight()); // keeps the shape
		assertThat(Math.max(thumb.getWidth(), thumb.getHeight())).isEqualTo(400);
	}

	@Test
	void smallPhotoIsNeverEnlarged() throws Exception {
		ProcessedImage result = processor.process(file("small.jpg", TestImages.jpeg(200, 300, 1)));

		BufferedImage full = decode(result.full());
		assertThat(full.getWidth()).isEqualTo(200);
		assertThat(full.getHeight()).isEqualTo(300);
		assertThat(decode(result.thumb()).getHeight()).isEqualTo(300);
	}

	@Test
	void pngWithTransparencyIsStoredAsJpeg() throws Exception {
		ProcessedImage result = processor.process(file("alpha.png", TestImages.transparentPng(120, 120)));

		assertThat(result.full()[0] & 0xFF).isEqualTo(0xFF);
		assertThat(result.full()[1] & 0xFF).isEqualTo(0xD8);
		assertThat(decode(result.full()).getWidth()).isEqualTo(120);
	}

	@Test
	void webpIsAccepted() throws Exception {
		ProcessedImage result = processor.process(file("photo.webp", Base64.getDecoder().decode(TINY_WEBP)));

		assertThat(decode(result.full())).isNotNull();
	}

	@Test
	void hashIsComputedFromTheStoredPicture() {
		ProcessedImage a = processor.process(file("a.jpg", TestImages.jpeg(800, 800, 1)));
		ProcessedImage b = processor.process(file("b.jpg", TestImages.jpeg(800, 800, 2)));

		assertThat(a.hash()).isNotEqualTo(b.hash());
	}

	@Test
	void textFilePretendingToBeAJpgIsRejectedByItsBytes() {
		var fake = file("photo.jpg", "this is not a picture".getBytes());

		assertThatThrownBy(() -> processor.process(fake)).isInstanceOfSatisfying(ApiException.class, e -> {
			assertThat(e.code()).isEqualTo("INVALID_IMAGE");
			assertThat(e.getMessage()).contains("photo.jpg");
		});
	}

	@Test
	void corruptPictureWithJpegHeaderIsRejected() {
		byte[] broken = new byte[200];
		broken[0] = (byte) 0xFF;
		broken[1] = (byte) 0xD8;
		broken[2] = (byte) 0xFF;

		assertThatThrownBy(() -> processor.process(file("broken.jpg", broken))).isInstanceOfSatisfying(
				ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_IMAGE"));
	}

	/** A landscape JPEG whose EXIF says "rotate 90 degrees to view", as a phone held upright writes it. */
	private static byte[] landscapeWithRotateTag() throws Exception {
		byte[] plain = TestImages.jpeg(400, 200, 1);
		byte[] exif = { (byte) 0xFF, (byte) 0xE1, 0x00, 0x22, 'E', 'x', 'i', 'f', 0, 0,
				'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // big-endian TIFF header, first directory at 8
				0x00, 0x01, // one entry
				0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x06, 0x00, 0x00, // Orientation = 6
				0x00, 0x00, 0x00, 0x00 }; // no next directory
		byte[] out = new byte[plain.length + exif.length];
		System.arraycopy(plain, 0, out, 0, 2); // the start-of-image marker stays first
		System.arraycopy(exif, 0, out, 2, exif.length);
		System.arraycopy(plain, 2, out, 2 + exif.length, plain.length - 2);
		return out;
	}

	@Test
	void exifRotationIsAppliedSoPhonePhotosAreNotSideways() throws Exception {
		ProcessedImage result = processor.process(file("upright.jpg", landscapeWithRotateTag()));

		BufferedImage full = decode(result.full());
		assertThat(full.getHeight()).isGreaterThan(full.getWidth());
		assertThat(decode(result.thumb()).getHeight()).isGreaterThan(decode(result.thumb()).getWidth());
	}

	@Test
	void manyPhotosAtOnceAllComeOutRight() throws Exception {
		var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
		try {
			var jobs = new java.util.ArrayList<java.util.concurrent.Future<ProcessedImage>>();
			for (int i = 0; i < 12; i++) {
				byte[] bytes = TestImages.jpeg(1800, 1200, i);
				jobs.add(pool.submit(() -> processor.process(file("p.jpg", bytes))));
			}
			for (var job : jobs) {
				assertThat(decode(job.get().full()).getWidth()).isEqualTo(1600);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

}
