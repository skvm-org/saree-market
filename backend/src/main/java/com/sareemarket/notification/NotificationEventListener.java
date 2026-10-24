package com.sareemarket.notification;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sareemarket.notification.event.CollectionPublished;
import com.sareemarket.notification.event.ItemsAdded;
import com.sareemarket.notification.event.ItemsRestocked;

/**
 * Turns seller actions into notifications. Runs only after the seller's change has been saved (a failed upload
 * notifies nobody) and off the seller's request thread (the seller never waits for the fan-out).
 */
@Component
class NotificationEventListener {

	private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

	private final List<NotificationChannel> channels;

	NotificationEventListener(List<NotificationChannel> channels) {
		this.channels = channels;
	}

	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void on(CollectionPublished e) {
		dispatch(new NotificationEvent(NotificationType.NEW_COLLECTION, e.sellerId(), e.collectionId(), e.itemCount()));
	}

	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void on(ItemsAdded e) {
		dispatch(new NotificationEvent(NotificationType.ITEMS_ADDED, e.sellerId(), e.collectionId(), e.itemCount()));
	}

	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void on(ItemsRestocked e) {
		dispatch(new NotificationEvent(NotificationType.BACK_IN_STOCK, e.sellerId(), e.collectionId(), e.itemCount()));
	}

	// One channel failing must not stop the others, and nothing here may reach the seller's request.
	private void dispatch(NotificationEvent event) {
		for (NotificationChannel channel : channels) {
			try {
				channel.deliver(event);
			}
			catch (RuntimeException e) {
				log.error("Could not deliver {} through {}", event, channel.getClass().getSimpleName(), e);
			}
		}
	}

}
