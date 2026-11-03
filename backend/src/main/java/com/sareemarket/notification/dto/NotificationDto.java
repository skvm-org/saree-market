package com.sareemarket.notification.dto;

import java.time.Instant;

import com.sareemarket.notification.NotificationType;

/**
 * One entry in the notifications list.
 *
 * @param itemCount photos covered; it grows while a recent unread notification is merged with newer uploads
 * @param message ready to show, e.g. "Lakshmi Silks added 18 sarees · Diwali soft silks"
 * @param read true once the reseller has opened it or marked all read
 * @param createdAt when the notification was first created
 * @param updatedAt when it last grew; the list is sorted by this
 * @param coverItemId id of a photo of the collection (or, for an order update, of the first saree ordered) for the
 * round thumbnail; null when there is none
 * @param orderId the order an ORDER_UPDATE is about; null for follower notifications, whose collectionId is set instead
 * @param orderStatus the order's new status for an ORDER_UPDATE; null otherwise
 */
public record NotificationDto(Long id, NotificationType type, int itemCount, Long sellerId, String shopName,
		Long collectionId, String collectionTitle, String message, boolean read, Instant createdAt,
		Instant updatedAt, Long coverItemId, Long orderId, String orderStatus) {
}
