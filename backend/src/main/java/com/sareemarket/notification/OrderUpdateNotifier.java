package com.sareemarket.notification;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sareemarket.notification.event.OrderUpdated;

/**
 * Writes one in-app notification for the customer when their order changes status, so the bell and the notifications
 * list show "Lakshmi Silks confirmed your order #12". Runs only after the seller's change has been saved, off the
 * seller's request thread, like the follower notifications. The customer's unread-order flag on the order itself is
 * set separately by OrderService and drives the orders badge.
 */
@Component
class OrderUpdateNotifier {

	private final NamedParameterJdbcTemplate jdbc;

	OrderUpdateNotifier(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Inserts a row for the order's customer, read from the order itself so the event stays small. A customer whose
	 * account has been disabled gets nothing. Has its own transaction because the listener thread is not inside the
	 * seller's transaction any more.
	 */
	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void on(OrderUpdated event) {
		jdbc.update("""
				INSERT INTO notification (reseller_id, seller_id, collection_id, type, item_count, order_id, order_status)
				SELECT o.customer_id, o.seller_id, NULL, 'ORDER_UPDATE', 1, o.id, :status
				FROM customer_order o JOIN app_user u ON u.id = o.customer_id AND u.enabled
				WHERE o.id = :order""", new MapSqlParameterSource("order", event.orderId()).addValue("status", event.status()));
	}

}
