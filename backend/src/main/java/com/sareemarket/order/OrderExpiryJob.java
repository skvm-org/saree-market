package com.sareemarket.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases payment holds and rejected-payment orders whose two hours ran out. Scheduling is enabled in AsyncConfig. */
@Component
class OrderExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

	private final HoldService holds;

	private final OrderService orders;

	OrderExpiryJob(HoldService holds, OrderService orders) {
		this.holds = holds;
		this.orders = orders;
	}

	@Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
	void run() {
		try {
			holds.expireOverdue();
			orders.expireRejected();
		}
		catch (RuntimeException e) {
			log.warn("Hold expiry run failed; it will try again in five minutes", e);
		}
	}

}
