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

}
