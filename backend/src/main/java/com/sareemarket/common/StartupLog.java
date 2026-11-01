package com.sareemarket.common;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** One line when the server is ready, saying which build runs and where it keeps photos: the first thing to read in a log. */
@Component
class StartupLog {

	private static final Logger log = LoggerFactory.getLogger(StartupLog.class);

	private final Environment env;

	private final JdbcTemplate jdbc;

	private final String build;

	StartupLog(Environment env, JdbcTemplate jdbc, @Value("${BUILD_SHA:unknown}") String build) {
		this.env = env;
		this.jdbc = jdbc;
		this.build = build;
	}

	@EventListener(ApplicationReadyEvent.class)
	void onReady() {
		String storage = env.getProperty("app.storage.type", "local");
		log.info("Backend ready: build={} profiles={} storage={} bucket={} fallbackToLocal={} schema={}",
				build.length() > 7 ? build.substring(0, 7) : build, Arrays.toString(env.getActiveProfiles()), storage,
				"s3".equals(storage) ? env.getProperty("app.storage.s3.bucket") : "-",
				env.getProperty("app.storage.s3.fallback-to-local"), schemaVersion());
	}

	private String schemaVersion() {
		try {
			return jdbc.queryForObject(
					"SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1",
					String.class);
		}
		catch (RuntimeException e) {
			return "unknown";
		}
	}

}
