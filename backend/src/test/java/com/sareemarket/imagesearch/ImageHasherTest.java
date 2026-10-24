package com.sareemarket.imagesearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.sareemarket.TestImages;

import net.coobird.thumbnailator.Thumbnails;

class ImageHasherTest {

	private final BufferedImage original = TestImages.blocks(400, 400, 1);

	@Test
	void identicalImageHasDistanceZero() {
		assertThat(ImageHasher.distance(ImageHasher.hash(original), ImageHasher.hash(original))).isZero();
	}

	@Test
	void resizedCopyIsClose() throws Exception {
		BufferedImage smaller = Thumbnails.of(original).size(200, 200).asBufferedImage();

		assertThat(ImageHasher.distance(ImageHasher.hash(original), ImageHasher.hash(smaller))).isLessThanOrEqualTo(8);
	}

	@Test
	void recompressedCopyIsClose() throws Exception {
		byte[] lowQuality = jpegAtQuality(original, 0.3f);
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(lowQuality));

		assertThat(ImageHasher.distance(ImageHasher.hash(original), ImageHasher.hash(decoded))).isLessThanOrEqualTo(8);
	}

	@Test
	void unrelatedImageIsFarAway() {
		long a = ImageHasher.hash(original);
		long b = ImageHasher.hash(TestImages.blocks(400, 400, 99));

		assertThat(ImageHasher.distance(a, b)).isGreaterThan(12);
	}

	private static byte[] jpegAtQuality(BufferedImage image, float quality) throws Exception {
		var out = new java.io.ByteArrayOutputStream();
		Thumbnails.of(image).scale(1.0).outputFormat("jpg").outputQuality(quality).toOutputStream(out);
		return out.toByteArray();
	}

}
