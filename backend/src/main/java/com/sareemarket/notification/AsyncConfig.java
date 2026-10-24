package com.sareemarket.notification;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
class AsyncConfig {

	/**
	 * One thread, so events are handled in the order they happened and a seller's uploads never overtake each
	 * other. Fan-out is a couple of queries, so one thread is plenty for the expected load.
	 */
	@Bean("notificationExecutor")
	Executor notificationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(1000);
		executor.setThreadNamePrefix("notify-");
		return executor;
	}

}
