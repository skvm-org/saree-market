package com.sareemarket.notification;

/**
 * One way of telling followers about an event. The app has only the in-app list today; push, WhatsApp or email
 * channels plug in here later without touching the code that raises the events. This is a deliberate extension
 * point: every Spring bean that implements it is picked up by NotificationEventListener automatically.
 *
 * Event flow: a feature service publishes a Spring event (CollectionPublished, ItemsAdded, ItemsRestocked) inside
 * its transaction. NotificationEventListener receives it with @TransactionalEventListener(AFTER_COMMIT), so only
 * saved changes notify anyone. The listener runs on the single-thread "notificationExecutor" (@Async, see
 * AsyncConfig), turns the event into a NotificationEvent and calls every channel.
 */
public interface NotificationChannel {

	/**
	 * Delivers the event to the followers of the seller. Called on the notification executor thread, after the
	 * seller's change has been committed. An exception is logged by the listener and does not stop other channels.
	 */
	void deliver(NotificationEvent event);

}
