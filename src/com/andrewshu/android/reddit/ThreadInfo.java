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

import org.codehaus.jackson.annotate.JsonAnySetter;

import android.text.SpannableStringBuilder;

/**
 * Class representing a thread posting in reddit API.
 * 
 * @author TalkLittle
 *
 */
public class ThreadInfo implements Serializable {
	static final long serialVersionUID = 39;
	
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
	
	private String author;
	private boolean clicked;
	private double created;
	private double created_utc;
	private String domain;
	private int downs;
	private boolean hidden;
	private String id;
	private String likes;
//	private MediaInfo media;
//	private MediaEmbedInfo media_embed;
	private String name;
	private int num_comments;
	private boolean saved;
	private int score;
	private String selftext;
	private String selftext_html;
	private String subreddit;
	private String subreddit_id;
	private String then;
	private String thumbnail;
	private String title;
	private int ups;
	private String url;
	
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
	
	@JsonAnySetter
	public void handleUnknown(String key, Object value) {
		// Ignore.
	}
	
	//
	// Getters
	//

	public String getAuthor() {
		return author;
	}

	public boolean isClicked() {
		return clicked;
	}

	public double getCreated() {
		return created;
	}

	public double getCreated_utc() {
		return created_utc;
	}

	public String getDomain() {
		return domain;
	}

	public int getDowns() {
		return downs;
	}

	public boolean isHidden() {
		return hidden;
	}

	public String getId() {
		return id;
	}

	public String getLikes() {
		return likes;
	}

	public String getName() {
		return name;
	}

	public int getNum_comments() {
		return num_comments;
	}

	public boolean isSaved() {
		return saved;
	}

	public int getScore() {
		return score;
	}

	public String getSelftext() {
		return selftext;
	}

	public String getSelftext_html() {
		return selftext_html;
	}

	public String getSubreddit() {
		return subreddit;
	}

	public String getSubreddit_id() {
		return subreddit_id;
	}

	public String getThen() {
		return then;
	}

	public String getThumbnail() {
		return thumbnail;
	}

	public String getTitle() {
		return title;
	}

	public int getUps() {
		return ups;
	}

	public String getUrl() {
		return url;
	}
	
	public SpannableStringBuilder getSSBSelftext() {
		return mSSBSelftext;
	}

	//
	// Setters
	//
	
	public void setAuthor(String author) {
		this.author = author;
	}

	public void setClicked(boolean clicked) {
		this.clicked = clicked;
	}

	public void setCreated(double created) {
		this.created = created;
	}

	public void setCreated_utc(double created_utc) {
		this.created_utc = created_utc;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public void setDowns(int downs) {
		this.downs = downs;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setLikes(String likes) {
		this.likes = likes;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setNum_comments(int num_comments) {
		this.num_comments = num_comments;
	}

	public void setSaved(boolean saved) {
		this.saved = saved;
	}

	public void setScore(int score) {
		this.score = score;
	}

	public void setSelftext(String selftext) {
		this.selftext = selftext;
	}

	public void setSelftext_html(String selftext_html) {
		this.selftext_html = selftext_html;
	}

	public void setSubreddit(String subreddit) {
		this.subreddit = subreddit;
	}

	public void setSubreddit_id(String subreddit_id) {
		this.subreddit_id = subreddit_id;
	}

	public void setThen(String then) {
		this.then = then;
	}

	public void setThumbnail(String thumbnail) {
		this.thumbnail = thumbnail;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setUps(int ups) {
		this.ups = ups;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void setSSBSelftext(SpannableStringBuilder selftext) {
		mSSBSelftext = selftext;
	}
}
