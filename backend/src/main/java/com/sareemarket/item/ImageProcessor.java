package com.sareemarket.item;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.common.ApiException;
import com.sareemarket.imagesearch.ImageHasher;

import net.coobird.thumbnailator.Thumbnails;

/** Checks an uploaded photo is a real JPG/PNG/WebP, then makes the stored copy, the thumbnail and the hash. */
@Component
public class ImageProcessor {

	static final int MAX_FULL = 1600;

	static final int MAX_THUMB = 400;

	private static final float QUALITY = 0.85f;

	/** Decoding a 10 MB photo takes tens of MB of heap; this many at once keeps a 30-photo upload from exhausting it. */
	static final int MAX_CONCURRENT = 3;

	private static final Logger log = LoggerFactory.getLogger(ImageProcessor.class);

	private final Semaphore slots = new Semaphore(MAX_CONCURRENT);

	public ProcessedImage process(MultipartFile file) {
		String name = file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename();
		byte[] original;
		try {
			original = file.getBytes();
		}
		catch (IOException e) {
			log.warn("Could not read upload \"{}\"", name, e);
			throw invalid(name);
		}
		// Look at the bytes, not the filename or Content-Type: both come from the client.
		if (!looksLikeImage(original)) {
			throw invalid(name);
		}
		slots.acquireUninterruptibly();
		try {
			byte[] full = resize(original, MAX_FULL);
			byte[] thumb = resize(full, MAX_THUMB);
			BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(full));
			return new ProcessedImage(full, thumb, ImageHasher.hash(decoded));
		}
		catch (IOException | RuntimeException e) {
			log.warn("Could not process upload \"{}\"", name, e);
			throw invalid(name);
		}
		finally {
			slots.release();
		}
	}

	private static ApiException invalid(String name) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE",
				"\"" + name + "\" is not a valid JPG, PNG or WebP photo");
	}

	private static boolean looksLikeImage(byte[] b) {
		boolean jpeg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
		boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
		boolean webp = b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W'
				&& b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
		return jpeg || png || webp;
	}

	/** Fits the picture inside max x max as JPEG, never enlarging it. Thumbnailator applies EXIF rotation. */
	private static byte[] resize(byte[] source, int max) throws IOException {
		var builder = Thumbnails.of(new ByteArrayInputStream(source));
		if (fitsWithin(source, max)) {
			builder.scale(1.0);
		}
		else {
			builder.size(max, max);
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		builder.outputFormat("jpg").outputQuality(QUALITY).toOutputStream(out);
		return out.toByteArray();
	}

	// Reads only the header, so a huge picture is not decoded twice.
	private static boolean fitsWithin(byte[] source, int max) throws IOException {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw new IOException("No reader for this image");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in);
				return reader.getWidth(0) <= max && reader.getHeight(0) <= max;
			}
			finally {
				reader.dispose();
			}
		}
	}

}
