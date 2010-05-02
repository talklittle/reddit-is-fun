package com.andrewshu.android.reddit;

public class CaptchaException extends Exception {
	static final long serialVersionUID = 40;
	
	public CaptchaException(String message) {
		super(message);
	}
}
