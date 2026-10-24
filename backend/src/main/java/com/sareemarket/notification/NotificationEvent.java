package com.sareemarket.notification;

/** What happened, in the form every delivery channel receives it. */
public record NotificationEvent(NotificationType type, long sellerId, long collectionId, int itemCount) {
}
