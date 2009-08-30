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
	
	
	static void dLong(String tag, String msg) {
    	int c;
    	boolean done = false;
    	StringBuilder sb = new StringBuilder();
    	for (int k = 0; k < msg.length(); k += 80) {
    		for (int i = 0; i < 80; i++) {
    			if (k + i >= msg.length()) {
    				done = true;
    				break;
    			}
    			c = msg.charAt(k + i);
    			sb.append((char) c);
    		}
    		Log.d(tag, "doReply response content: " + sb.toString());
    		sb = new StringBuilder();
    		if (done)
    			break;
    	}
	}
}
