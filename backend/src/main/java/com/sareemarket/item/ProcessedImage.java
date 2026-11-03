package com.sareemarket.item;

/**
 * A checked upload ready to store.
 *
 * @param full JPEG bytes of the picture, at most 1600 px on the longest side
 * @param thumb JPEG bytes of the thumbnail, at most 400 px
 * @param hash 64-bit dHash from {@link com.sareemarket.imagesearch.ImageHasher}, kept for image search
 */
public record ProcessedImage(byte[] full, byte[] thumb, long hash) {
}
