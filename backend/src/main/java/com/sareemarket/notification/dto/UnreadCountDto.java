package com.sareemarket.notification.dto;

/**
 * Response of the unread-count endpoint.
 *
 * @param count notifications the caller has not read
 */
public record UnreadCountDto(long count) {
}
