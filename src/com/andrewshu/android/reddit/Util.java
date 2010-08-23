/*
 * Copyright 2009 Andrew Shu
 *
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

import java.util.ArrayList;

import android.net.Uri;
import android.text.style.URLSpan;

public class Util {
	
	public static ArrayList<String> extractUris(URLSpan[] spans) {
        int size = spans.length;
        ArrayList<String> accumulator = new ArrayList<String>();

        for (int i = 0; i < size; i++) {
            accumulator.add(spans[i].getURL());
        }
        return accumulator;
    }
	
	/**
	 * Convert HTML tags so they will be properly recognized by
	 * android.text.Html.fromHtml()
	 * @param html unescaped HTML
	 * @return converted HTML
	 */
	public static String convertHtmlTags(String html) {
		// Handle <code>
		html = html.replaceAll("<code>", "<tt>").replaceAll("</code>", "</tt>");
		
		// Handle <pre>
		int preIndex = html.indexOf("<pre>");
		int preEndIndex = -6;  // -"</pre>".length()
		StringBuilder bodyConverted = new StringBuilder();
		while (preIndex != -1) {
			// get the text between previous </pre> and next <pre>.
			bodyConverted = bodyConverted.append(html.substring(preEndIndex + 6, preIndex));
			preEndIndex = html.indexOf("</pre>", preIndex);
			// Replace newlines with <br> inside the <pre></pre>
			// Retain <pre> tags since android.text.Html.fromHtml() will ignore them anyway.
			bodyConverted = bodyConverted.append(html.substring(preIndex, preEndIndex).replaceAll("\n", "<br>"))
				.append("</pre>");
			preIndex = html.indexOf("<pre>", preEndIndex);
		}
		return bodyConverted.append(html.substring(preEndIndex + 6)).toString();
	}
	
	/**
	 * To the second, not millisecond like reddit
	 * @param timeSeconds
	 * @return
	 */
	public static String getTimeAgo(long utcTimeSeconds) {
		long systime = System.currentTimeMillis() / 1000;
		long diff = systime - utcTimeSeconds;
		if (diff <= 0)
			return "very recently";
		else if (diff < 60) {
			if (diff == 1)
				return "1 second ago";
			else
				return diff + " seconds ago";
		}
		else if (diff < 3600) {
			if ((diff / 60) == 1)
				return "1 minute ago";
			else
				return (diff / 60) + " minutes ago";
		}
		else if (diff < 86400) { // 86400 seconds in a day
			if ((diff / 3600) == 1)
				return "1 hour ago";
			else
				return (diff / 3600) + " hours ago";
		}
		else if (diff < 604800) { // 86400 * 7
			if ((diff / 86400) == 1)
				return "1 day ago";
			else
				return (diff / 86400) + " days ago";
		}
		else if (diff < 2592000) { // 86400 * 30
			if ((diff / 604800) == 1)
				return "1 week ago";
			else
				return (diff / 604800) + " weeks ago";
		}
		else if (diff < 31536000) { // 86400 * 365
			if ((diff / 2592000) == 1)
				return "1 month ago";
			else
				return (diff / 2592000) + " months ago";
		}
		else {
			if ((diff / 31536000) == 1)
				return "1 year ago";
			else
				return (diff / 31536000) + " years ago";
		}
	}
	
	public static String getTimeAgo(double utcTimeSeconds) {
		return getTimeAgo((long)utcTimeSeconds);
	}
	
	public static String showNumComments(int comments) {
		if (comments == 1) {
			return "1 comment";
		} else {
			return comments + " comments";
		}
	}
	
	public static String showNumPoints(int score) {
		if (score == 1) {
			return "1 point";
		} else {
			return score + " points";
		}
	}
	
	static String absolutePathToURL(String path) {
		if (path.startsWith("/"))
			return "http://www.reddit.com" + path;
		return path;
	}
	
	
	// ===============
	//       Uri
	// ===============
	
	static Uri createCommentUri(String subreddit, String threadId, String commentId, int commentContext) {
		return Uri.parse(new StringBuilder("http://www.reddit.com/r/")
			.append(subreddit)
			.append("/comments/")
			.append(threadId)
			.append("/z/")
			.append(commentId)
			.append("?context=")
			.append(commentContext)
			.toString());
	}
	
	static Uri createCommentUri(ThingInfo commentThingInfo) {
		return Uri.parse(new StringBuilder("http://www.reddit.com/r/")
			.append(commentThingInfo.getContext())
			.toString());
	}
	
	static Uri createSubmitUri(String subreddit) {
		if (Constants.FRONTPAGE_STRING.equals(subreddit))
			return Uri.parse("http://www.reddit.com/submit");
		
		return Uri.parse(new StringBuilder("http://www.reddit.com/r/")
			.append(subreddit)
			.append("/submit")
			.toString());
	}
	
	static Uri createSubmitUri(ThingInfo thingInfo) {
		return createSubmitUri(thingInfo.getSubreddit());
	}
	
	static Uri createSubredditUri(String subreddit) {
		if (Constants.FRONTPAGE_STRING.equals(subreddit))
			return Uri.parse("http://www.reddit.com/");
		
		return Uri.parse(new StringBuilder("http://www.reddit.com/r/")
			.append(subreddit)
			.toString());
	}
	
	static Uri createSubredditUri(ThingInfo thingInfo) {
		return createSubredditUri(thingInfo.getSubreddit());
	}
	
	static Uri createThreadUri(String subreddit, String threadId) {
		return Uri.parse(new StringBuilder("http://www.reddit.com/r/")
			.append(subreddit)
			.append("/comments/")
			.append(threadId)
			.toString());
	}
	
	static Uri createThreadUri(ThingInfo threadThingInfo) {
		return createThreadUri(threadThingInfo.getSubreddit(), threadThingInfo.getId());
	}
	
	static boolean isRedditUri(Uri uri) {
		if (uri == null) return false;
		String host = uri.getHost();
		return host != null && host.endsWith(".reddit.com");
	}
	
    /**
     * Creates mobile version of <code>uri</code> if applicable.
     * 
     * @return original uri if no mobile version of uri is known
     */
    static Uri optimizeMobileUri(Uri uri) {
    	if (isWikipediaUri(uri)) {
    		uri = createMobileWikpediaUri(uri);
    	}
    	return uri;
    }
    
    /**
     * @return if uri points to a non-mobile wikpedia uri.
     */
    static boolean isWikipediaUri(Uri uri) {
    	if (uri == null) return false;
    	String host = uri.getHost();
    	return host != null && host.endsWith(".wikipedia.org") && !host.contains(".m.wikipedia.org");
    }
    
    /**
     * @return mobile version of a wikipedia uri
     */
    static Uri createMobileWikpediaUri(Uri uri) {
    	String uriString = uri.toString();
    	return Uri.parse(uriString.replace(".wikipedia.org/", ".m.wikipedia.org/"));
    }
    
    static boolean isYoutubeUri(Uri uri) {
    	String host = uri.getHost();
    	return host.endsWith(".youtube.com");
    }
    
}
