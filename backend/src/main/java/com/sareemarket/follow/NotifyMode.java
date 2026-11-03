package com.sareemarket.follow;

/**
 * How a reseller hears about a followed seller's new collections. INSTANT sends a notification for each one;
 * MUTED keeps the follow (the Following filter and list) but sends nothing. Stored as text in seller_follow.notify_mode.
 */
public enum NotifyMode {
	INSTANT, MUTED
}
