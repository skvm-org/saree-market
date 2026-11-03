package com.sareemarket.notification;

/**
 * What happened, in the form every delivery channel receives it. Built by NotificationEventListener from the
 * feature events, so channels do not depend on those event types.
 *
 * @param type what kind of change it was
 * @param sellerId the seller whose followers are told
 * @param collectionId the collection the change belongs to
 * @param itemCount how many photos (sarees) the change covers
 */
public record NotificationEvent(NotificationType type, long sellerId, long collectionId, int itemCount) {
}
