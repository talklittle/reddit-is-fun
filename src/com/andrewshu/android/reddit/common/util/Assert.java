package com.andrewshu.android.reddit.common.util;

/**
 * Based on Spring's Assert class
 */
public class Assert {

	public static void state(boolean state, String message) {
		if (!state)
			throw new IllegalStateException(message);
	}

	public static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null) {
			if (actual != null) {
				throw new IllegalStateException("assertEquals failed: expected null, actual " + actual + "; " + message);
			}
		}
		else if (!expected.equals(actual)) {
			throw new IllegalStateException("assertEquals failed: expected " + expected + ", actual " + actual + "; " + message);
		}
	}
	
	
	
	
}
