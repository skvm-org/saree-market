package com.sareemarket.notification;

/**
 * One way of telling followers about an event. The app has only the in-app list today; push, WhatsApp or email
 * channels plug in here later without touching the code that raises the events.
 */
public interface NotificationChannel {

	void deliver(NotificationEvent event);

}
