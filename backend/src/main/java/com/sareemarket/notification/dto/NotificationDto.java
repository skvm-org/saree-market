package com.sareemarket.notification.dto;

import java.time.Instant;

import com.sareemarket.notification.NotificationType;

/** message is ready to show, e.g. "Lakshmi Silks added 18 sarees · Diwali soft silks". */
public record NotificationDto(Long id, NotificationType type, int itemCount, Long sellerId, String shopName,
		Long collectionId, String collectionTitle, String message, boolean read, Instant createdAt,
		Instant updatedAt) {
}
