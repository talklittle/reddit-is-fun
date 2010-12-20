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

import java.lang.reflect.Method;
import java.util.ArrayList;

import org.apache.http.HttpResponse;

import android.app.Activity;
import android.net.Uri;
import android.text.style.URLSpan;
import android.util.Log;

public class Util {
	
	private static final String TAG = "Util";
	
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
		html = bodyConverted.append(html.substring(preEndIndex + 6)).toString();
		
		// Handle <li>
		html = html.replaceAll("<li>", "* ").replaceAll("</li>", "<br>");
		
		// Handle <strong> and <em>, which are normally <b> and <i> respectively, but reversed in Android.
		// ANDROID BUG: http://code.google.com/p/android/issues/detail?id=3473
		html = html.replaceAll("<strong>", "<b>").replaceAll("</strong>", "</b>")
		           .replaceAll("<em>", "<i>").replaceAll("</em>", "</i>");
		
		return html;
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
	
	public static boolean isHttpStatusOK(HttpResponse response) {
		if (response == null || response.getStatusLine() == null) {
			return false;
		}
		return response.getStatusLine().getStatusCode() == 200;
	}
	
	// ===============
	//      Theme
	// ===============

	static boolean isLightTheme(int theme) {
		return theme == R.style.Reddit_Light_Medium || theme == R.style.Reddit_Light_Large || theme == R.style.Reddit_Light_Larger;
	}
	
	static boolean isDarkTheme(int theme) {
		return theme == R.style.Reddit_Dark_Medium || theme == R.style.Reddit_Dark_Large || theme == R.style.Reddit_Dark_Larger;
	}
	
	static int getInvertedTheme(int theme) {
		switch (theme) {
		case R.style.Reddit_Light_Medium:
			return R.style.Reddit_Dark_Medium;
		case R.style.Reddit_Light_Large:
			return R.style.Reddit_Dark_Large;
		case R.style.Reddit_Light_Larger:
			return R.style.Reddit_Dark_Larger;
		case R.style.Reddit_Dark_Medium:
			return R.style.Reddit_Light_Medium;
		case R.style.Reddit_Dark_Large:
			return R.style.Reddit_Light_Large;
		case R.style.Reddit_Dark_Larger:
			return R.style.Reddit_Light_Larger;
		default:
			return R.style.Reddit_Light_Medium;	
		}
	}
	
	static int getThemeResourceFromPrefs(String themePref, String textSizePref) {
		if (Constants.PREF_THEME_LIGHT.equals(themePref)) {
			if (Constants.PREF_TEXT_SIZE_MEDIUM.equals(textSizePref))
				return R.style.Reddit_Light_Medium;
			else if (Constants.PREF_TEXT_SIZE_LARGE.equals(textSizePref))
				return R.style.Reddit_Light_Large;
			else if (Constants.PREF_TEXT_SIZE_LARGER.equals(textSizePref))
				return R.style.Reddit_Light_Larger;
		} else /* if (Constants.PREF_THEME_DARK.equals(themePref)) */ {
			if (Constants.PREF_TEXT_SIZE_MEDIUM.equals(textSizePref))
				return R.style.Reddit_Dark_Medium;
			else if (Constants.PREF_TEXT_SIZE_LARGE.equals(textSizePref))
				return R.style.Reddit_Dark_Large;
			else if (Constants.PREF_TEXT_SIZE_LARGER.equals(textSizePref))
				return R.style.Reddit_Dark_Larger;
		}
		return R.style.Reddit_Light_Medium;
	}
	
	/**
	 * Return the theme and textSize String prefs
	 */
	static String[] getPrefsFromThemeResource(int theme) {
		switch (theme) {
		case R.style.Reddit_Light_Medium:
			return new String[] { Constants.PREF_THEME_LIGHT, Constants.PREF_TEXT_SIZE_MEDIUM };
		case R.style.Reddit_Light_Large:
			return new String[] { Constants.PREF_THEME_LIGHT, Constants.PREF_TEXT_SIZE_LARGE };
		case R.style.Reddit_Light_Larger:
			return new String[] { Constants.PREF_THEME_LIGHT, Constants.PREF_TEXT_SIZE_LARGER };
		case R.style.Reddit_Dark_Medium:
			return new String[] { Constants.PREF_THEME_DARK, Constants.PREF_TEXT_SIZE_MEDIUM };
		case R.style.Reddit_Dark_Large:
			return new String[] { Constants.PREF_THEME_DARK, Constants.PREF_TEXT_SIZE_LARGE };
		case R.style.Reddit_Dark_Larger:
			return new String[] { Constants.PREF_THEME_DARK, Constants.PREF_TEXT_SIZE_LARGER };
		default:
			return new String[] { Constants.PREF_THEME_LIGHT, Constants.PREF_TEXT_SIZE_MEDIUM };
		}
	}
	
	static int getTextAppearanceResource(int themeResource, int androidTextAppearanceStyle) {
		switch (themeResource) {
		case R.style.Reddit_Light_Medium:
		case R.style.Reddit_Dark_Medium:
			switch (androidTextAppearanceStyle) {
			case android.R.style.TextAppearance_Small:
				return R.style.TextAppearance_Medium_Small;
			case android.R.style.TextAppearance_Medium:
				return R.style.TextAppearance_Medium_Medium;
			case android.R.style.TextAppearance_Large:
				return R.style.TextAppearance_Medium_Large;
			default:
				return R.style.TextAppearance_Medium_Medium;
			}
		case R.style.Reddit_Light_Large:
		case R.style.Reddit_Dark_Large:
			switch (androidTextAppearanceStyle) {
			case android.R.style.TextAppearance_Small:
				return R.style.TextAppearance_Large_Small;
			case android.R.style.TextAppearance_Medium:
				return R.style.TextAppearance_Large_Medium;
			case android.R.style.TextAppearance_Large:
				return R.style.TextAppearance_Large_Large;
			default:
				return R.style.TextAppearance_Large_Medium;
			}
		case R.style.Reddit_Light_Larger:
		case R.style.Reddit_Dark_Larger:
			switch (androidTextAppearanceStyle) {
			case android.R.style.TextAppearance_Small:
				return R.style.TextAppearance_Larger_Small;
			case android.R.style.TextAppearance_Medium:
				return R.style.TextAppearance_Larger_Medium;
			case android.R.style.TextAppearance_Large:
				return R.style.TextAppearance_Larger_Large;
			default:
				return R.style.TextAppearance_Larger_Medium;
			}
		default:
			return R.style.TextAppearance_Medium_Medium;	
		}
	}
	
	
	// =======================
	//    Mail Notification
	// =======================
	
	static long getMillisFromMailNotificationPref(String pref) {
		if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_OFF.equals(pref)) {
			return 0;
		} else if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_5MIN.equals(pref)) {
			return 5 * 60 * 1000;
		} else if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_30MIN.equals(pref)) {
			return 30 * 60 * 1000;
		} else if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_1HOUR.equals(pref)) {
			return 1 * 3600 * 1000;
		} else if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_6HOURS.equals(pref)) {
			return 6 * 3600 * 1000;
		} else /* if (Constants.PREF_MAIL_NOTIFICATION_SERVICE_1DAY.equals(pref)) */ {
			return 24 * 3600 * 1000;
		}
	}
	
	
	// ===============
	//   Transitions
	// ===============
	
	static void overridePendingTransition(Method activity_overridePendingTransition, Activity act, int enterAnim, int exitAnim) {
		// only available in Android 2.0 (SDK Level 5) and later
		if (activity_overridePendingTransition != null) {
			try {
				activity_overridePendingTransition.invoke(act, enterAnim, exitAnim);
			} catch (Exception ex) {
				if (Constants.LOGGING) Log.e(TAG, "overridePendingTransition", ex);
			}
		}
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
	
	static Uri createProfileUri(String username) {
		return Uri.parse(new StringBuilder("http://www.reddit.com/user/")
			.append(username)
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
    	if (uri == null) return false;
    	String host = uri.getHost();
    	return host != null && host.endsWith(".youtube.com");
    }
    
}
