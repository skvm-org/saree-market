package com.sareemarket.imagesearch;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;

import net.coobird.thumbnailator.Thumbnails;

/**
 * 64-bit difference hash (dHash). Shrink to 9x8 grey, then each bit says whether a pixel is brighter than its
 * right neighbour. Similar pictures (resized, recompressed, lightly cropped) give hashes a few bits apart.
 */
public final class ImageHasher {

	private static final int WIDTH = 9;

	private static final int HEIGHT = 8;

	private ImageHasher() {
	}

	public static long hash(BufferedImage image) {
		BufferedImage small;
		try {
			small = Thumbnails.of(image).forceSize(WIDTH, HEIGHT).asBufferedImage();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		long hash = 0;
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH - 1; x++) {
				hash <<= 1;
				if (luma(small.getRGB(x, y)) > luma(small.getRGB(x + 1, y))) {
					hash |= 1;
				}
			}
		}
		return hash;
	}

	/** Number of differing bits: 0 means identical hashes. */
	public static int distance(long a, long b) {
		return Long.bitCount(a ^ b);
	}

	private static int luma(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return (299 * r + 587 * g + 114 * b) / 1000;
	}

}
