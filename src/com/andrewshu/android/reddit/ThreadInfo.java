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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import android.text.SpannableStringBuilder;

/**
 * Class representing a thread posting in reddit API.
 * 
 * @author TalkLittle
 *
 */
public class ThreadInfo implements Serializable {
	static final long serialVersionUID = 29;
	
	public static final String AUTHOR        = "author";
	public static final String CLICKED       = "clicked";
	public static final String CREATED       = "created";
	public static final String CREATED_UTC   = "created_utc";
	public static final String DOMAIN        = "domain";
	public static final String DOWNS         = "downs";
	public static final String HIDDEN        = "hidden";
	public static final String ID            = "id";
	public static final String KIND          = "kind";
	public static final String LIKES         = "likes";
	public static final String MEDIA         = "media";
	public static final String MEDIA_EMBED   = "media_embed";
	public static final String NAME          = "name";
	public static final String NUM_COMMENTS  = "num_comments";
	public static final String SAVED         = "saved";
	public static final String SCORE         = "score";
	public static final String SELFTEXT      = "selftext";
	public static final String SELFTEXT_HTML = "selftext_html";
	public static final String SUBREDDIT     = "subreddit";
	public static final String SUBREDDIT_ID  = "subreddit_id";
	public static final String THEN          = "then";
	public static final String THUMBNAIL     = "thumbnail";
	public static final String TITLE         = "title";
	public static final String UPS           = "ups";
	public static final String URL           = "url";
	
	public static final String[] _KEYS = {
		AUTHOR, CLICKED, CREATED, CREATED_UTC, DOMAIN, DOWNS, HIDDEN,
		ID, KIND, LIKES, MEDIA, NAME, NUM_COMMENTS, SAVED, SCORE, SELFTEXT,
		SUBREDDIT, SUBREDDIT_ID, THEN, THUMBNAIL, TITLE, UPS, URL
	};
	
	public HashMap<String, String> mValues = new HashMap<String, String>();
	public final ArrayList<MarkdownURL> mUrls = new ArrayList<MarkdownURL>();
//	public JsonNode mMediaEmbed; // Unused.
	transient public SpannableStringBuilder mSSBSelftext = null;
	
	public void put(String key, String value) {
		mValues.put(key, value);
	}
	
	public void setDowns(String downs) {
		mValues.put(DOWNS, downs);
	}
	
	public void setLikes(String likes) {
		mValues.put(LIKES, likes);
	}
	
	public void setNumComments(String numComments) {
		mValues.put(NUM_COMMENTS, numComments);
	}
	
	public void setScore(String score) {
		mValues.put(SCORE, score);
	}
	
	public void setUps(String ups) {
		mValues.put(UPS, ups);
	}

	public String getAuthor() {
		return mValues.get(AUTHOR);
	}
	
	public String getClicked() {
		return mValues.get(CLICKED);
	}
	
	public String getCreated() {
		return mValues.get(CREATED);
	}
	
	public String getCreatedUtc() {
		return mValues.get(CREATED_UTC);
	}

	public String getDomain() {
		return mValues.get(DOMAIN);
	}
	
	public String getDowns() {
		return mValues.get(DOWNS);
	}
	
	public String getId() {
		return mValues.get(ID);
	}
	
	public String getKind() {
		return mValues.get(KIND);
	}
	
	public String getLikes() {
		return mValues.get(LIKES);
	}
	
	public String getName() {
		return mValues.get(NAME);
	}
	
	public String getNumComments() {
		return mValues.get(NUM_COMMENTS);
	}
	
	public String getScore() {
		return mValues.get(SCORE);
	}
	
	public String getSelftext() {
		return mValues.get(SELFTEXT);
	}
	
	public String getSelftextHtml() {
		return mValues.get(SELFTEXT_HTML);
	}
	
	public String getSubreddit() {
		return mValues.get(SUBREDDIT);
	}
	
	public String getTitle() {
		return mValues.get(TITLE);
	}
	
	public String getThumbnail() {
		return mValues.get(THUMBNAIL);
	}
	
	public String getUps() {
		return mValues.get(UPS);
	}

	public String getURL() {
		return mValues.get(URL);
	}
	
	
}
