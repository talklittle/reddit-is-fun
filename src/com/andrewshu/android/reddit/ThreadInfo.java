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
import java.util.Map;

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
		ID, LIKES, MEDIA, NAME, NUM_COMMENTS, SAVED, SCORE, SELFTEXT,
		SUBREDDIT, SUBREDDIT_ID, THEN, THUMBNAIL, TITLE, UPS, URL
	};
	
	public Map mData;
	public String mKind;
	public final ArrayList<MarkdownURL> mUrls = new ArrayList<MarkdownURL>();
//	public JsonNode mMediaEmbed; // Unused.
	transient public SpannableStringBuilder mSSBSelftext = null;
	
	public void setData(Map data) {
		mData = data;
	}
	
	// XXX XXX XXX: get rid of this
	public void put(String key, String value) {
		mData.put(key, value);
	}
	
	public void setDowns(Integer downs) {
		mData.put(DOWNS, downs);
	}
	
	public void setId(String id) {
		mData.put(ID, id);
	}
	
	public void setLikes(String likes) {
		mData.put(LIKES, likes);
	}
	
	public void setNumComments(Integer numComments) {
		mData.put(NUM_COMMENTS, numComments);
	}
	
	public void setScore(Integer score) {
		mData.put(SCORE, score);
	}
	
	public void setSubreddit(String subreddit) {
		mData.put(SUBREDDIT, subreddit);
	}
	
	public void setTitle(String title) {
		mData.put(TITLE, title);
	}
	
	public void setUps(Integer ups) {
		mData.put(UPS, ups);
	}

	public String getAuthor() {
		return (String) mData.get(AUTHOR);
	}
	
	public String getClicked() {
		return (String) mData.get(CLICKED);
	}
	
	public Double getCreated() {
		return (Double) mData.get(CREATED);
	}
	
	public Double getCreatedUtc() {
		return (Double) mData.get(CREATED_UTC);
	}

	public String getDomain() {
		return (String) mData.get(DOMAIN);
	}
	
	public Integer getDowns() {
		return (Integer) mData.get(DOWNS);
	}
	
	public String getId() {
		return (String) mData.get(ID);
	}
	
	public String getKind() {
		return mKind;
	}
	
	public String getLikes() {
		return (String) mData.get(LIKES);
	}
	
	public String getName() {
		return (String) mData.get(NAME);
	}
	
	public Integer getNumComments() {
		return (Integer) mData.get(NUM_COMMENTS);
	}
	
	public Integer getScore() {
		return (Integer) mData.get(SCORE);
	}
	
	public String getSelftext() {
		return (String) mData.get(SELFTEXT);
	}
	
	public String getSelftextHtml() {
		return (String) mData.get(SELFTEXT_HTML);
	}
	
	public String getSubreddit() {
		return (String) mData.get(SUBREDDIT);
	}
	
	public String getTitle() {
		return (String) mData.get(TITLE);
	}
	
	public String getThumbnail() {
		return (String) mData.get(THUMBNAIL);
	}
	
	public Integer getUps() {
		return (Integer) mData.get(UPS);
	}

	public String getURL() {
		return (String) mData.get(URL);
	}
	
	
}
