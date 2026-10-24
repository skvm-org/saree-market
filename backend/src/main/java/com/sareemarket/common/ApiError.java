package com.sareemarket.common;

import java.util.Map;

public record ApiError(String code, String message, Map<String, String> fields) {
}
