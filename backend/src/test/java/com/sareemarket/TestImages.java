package com.sareemarket;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;

import javax.imageio.ImageIO;

/** Small generated pictures for tests. Each seed gives a different blocky pattern. */
public final class TestImages {

	private TestImages() {
	}

	public static BufferedImage blocks(int width, int height, long seed) {
		Random random = new Random(seed);
		int block = 8;
		int[][] grey = new int[height / block + 1][width / block + 1];
		for (int[] row : grey) {
			for (int x = 0; x < row.length; x++) {
				row[x] = random.nextInt(256);
			}
		}
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int g = grey[y / block][x / block];
				image.setRGB(x, y, (g << 16) | (g << 8) | g);
			}
		}
		return image;
	}

	public static byte[] jpeg(int width, int height, long seed) {
		return encode(blocks(width, height, seed), "jpg");
	}

	/** A PNG with see-through pixels, which JPEG cannot store. */
	public static byte[] transparentPng(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, x < width / 2 ? 0x00000000 : 0xFFAA2244);
			}
		}
		return encode(image, "png");
	}

	public static byte[] encode(BufferedImage image, String format) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, format, out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
