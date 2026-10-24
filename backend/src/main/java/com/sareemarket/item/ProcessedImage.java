package com.sareemarket.item;

public record ProcessedImage(byte[] full, byte[] thumb, long hash) {
}
