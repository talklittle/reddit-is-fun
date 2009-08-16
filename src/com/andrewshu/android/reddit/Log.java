package com.andrewshu.android.reddit;

public class Log {

    /**
     * Disable this when releasing!
     */
	static int d(String tag, String msg) {
		return android.util.Log.d(tag, msg);
	}
	
    /**
     * Disable this when releasing!
     */
	static int e(String tag, String msg) {
		return android.util.Log.e(tag, msg);
	}
}
