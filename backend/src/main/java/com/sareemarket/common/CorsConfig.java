package com.sareemarket.common;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets browser apps on the origins in {@code app.cors.allowed-origins} call {@code /api/**}. The list is empty by
 * default, which adds no CORS mapping at all: the native apps do not need it, it exists for the web app added later.
 */
@Configuration
@EnableConfigurationProperties(CorsConfig.CorsProperties.class)
class CorsConfig implements WebMvcConfigurer {

	/** @param allowedOrigins exact origins such as {@code http://localhost:4200}; null or empty turns CORS off */
	@ConfigurationProperties("app.cors")
	record CorsProperties(List<String> allowedOrigins) {
	}

	private final CorsProperties properties;

	CorsConfig(CorsProperties properties) {
		this.properties = properties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (properties.allowedOrigins() == null || properties.allowedOrigins().isEmpty()) {
			return;
		}
		registry.addMapping("/api/**")
			.allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
			.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("*");
	}

}
