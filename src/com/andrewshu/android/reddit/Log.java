/*
 * This file is part of "reddit is fun".
 *
 * "reddit is fun" is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * "reddit is fun" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "reddit is fun".  If not, see <http://www.gnu.org/licenses/>.
 */

package com.andrewshu.android.reddit;

public class Log {

    /**
     * Disable this when releasing!
     */
	static int d(String tag, String msg) {
		return android.util.Log.d(tag, msg);
//		return 0;
	}
	
    /**
     * Disable this when releasing!
     */
	static int e(String tag, String msg) {
		return android.util.Log.e(tag, msg);
//		return 0;
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
