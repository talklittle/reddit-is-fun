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

import org.codehaus.jackson.annotate.JsonAnySetter;

import android.text.SpannableStringBuilder;

/**
 * Class representing a thread posting in reddit API.
 * 
 * @author TalkLittle
 *
 */
public class ThingInfo implements Serializable {
	static final long serialVersionUID = 39;
	
	private String author;					// t c
	private String body;					//   c
	private String body_html;				//   c
	private boolean clicked;				// t
	private double created;					// t c
	private double created_utc;				// t c
	private String domain;					// t
	private int downs;						// t c
	private boolean hidden;					// t
	private String id;						// t c
	private boolean is_self;				// t
	private Boolean likes;					// t c
	private String link_id;					//   c
//	private MediaInfo media;				// t	// TODO
//	private MediaEmbedInfo media_embed;		// t	// TODO
	private String name;					// t c
	private int num_comments;				// t
	private boolean over_18;				// t
	private String parent_id;				//   c
	private String permalink;				// t
	private Listing replies;				//   c
	private boolean saved;					// t
	private int score;						// t
	private String selftext;				// t
	private String selftext_html;			// t
	private String subreddit;				// t c
	private String subreddit_id;			// t
	private String thumbnail;				// t
	private String title;					// t
	private int ups;						// t c
	private String url;						// t
	
	public final ArrayList<MarkdownURL> mUrls = new ArrayList<MarkdownURL>();
	transient private SpannableStringBuilder mSSBSelftext = null;
	transient private SpannableStringBuilder mSSBBody = null;
	
	private int mIndent = 0;
	private String mReplyDraft = null;
	
	@JsonAnySetter
	public void handleUnknown(String key, Object value) {
		// Ignore.
	}
	
	//
	// Getters and setters
	//

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getBody_html() {
		return body_html;
	}

	public void setBody_html(String body_html) {
		this.body_html = body_html;
	}

	public boolean isClicked() {
		return clicked;
	}

	public void setClicked(boolean clicked) {
		this.clicked = clicked;
	}

	public double getCreated() {
		return created;
	}

	public void setCreated(double created) {
		this.created = created;
	}

	public double getCreated_utc() {
		return created_utc;
	}

	public void setCreated_utc(double created_utc) {
		this.created_utc = created_utc;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public int getDowns() {
		return downs;
	}

	public void setDowns(int downs) {
		this.downs = downs;
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public boolean isIs_self() {
		return is_self;
	}

	public void setIs_self(boolean is_self) {
		this.is_self = is_self;
	}

	public boolean isOver_18() {
		return over_18;
	}

	public void setOver_18(boolean over_18) {
		this.over_18 = over_18;
	}

	public String getPermalink() {
		return permalink;
	}

	public void setPermalink(String permalink) {
		this.permalink = permalink;
	}

	public Boolean getLikes() {
		return likes;
	}

	public void setLikes(Boolean likes) {
		this.likes = likes;
	}

	public String getLink_id() {
		return link_id;
	}

	public void setLink_id(String link_id) {
		this.link_id = link_id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getNum_comments() {
		return num_comments;
	}

	public void setNum_comments(int num_comments) {
		this.num_comments = num_comments;
	}

	public String getParent_id() {
		return parent_id;
	}

	public void setParent_id(String parent_id) {
		this.parent_id = parent_id;
	}

	public Listing getReplies() {
		return replies;
	}

	public void setReplies(Listing replies) {
		this.replies = replies;
	}

	public boolean isSaved() {
		return saved;
	}

	public void setSaved(boolean saved) {
		this.saved = saved;
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}

	public String getSelftext() {
		return selftext;
	}

	public void setSelftext(String selftext) {
		this.selftext = selftext;
	}

	public String getSelftext_html() {
		return selftext_html;
	}

	public void setSelftext_html(String selftext_html) {
		this.selftext_html = selftext_html;
	}

	public String getSubreddit() {
		return subreddit;
	}

	public void setSubreddit(String subreddit) {
		this.subreddit = subreddit;
	}

	public String getSubreddit_id() {
		return subreddit_id;
	}

	public void setSubreddit_id(String subreddit_id) {
		this.subreddit_id = subreddit_id;
	}

	public String getThumbnail() {
		return thumbnail;
	}

	public void setThumbnail(String thumbnail) {
		this.thumbnail = thumbnail;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public int getUps() {
		return ups;
	}

	public void setUps(int ups) {
		this.ups = ups;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public SpannableStringBuilder getSSBSelftext() {
		return mSSBSelftext;
	}

	public void setSSBSelftext(SpannableStringBuilder selftext) {
		mSSBSelftext = selftext;
	}

	public ArrayList<MarkdownURL> getUrls() {
		return mUrls;
	}
	
	public int getIndent() {
		return mIndent;
	}
	
	public void setIndent(int indent) {
		mIndent = indent;
	}
	
	public String getReplyDraft() {
		return mReplyDraft;
	}
	
	public void setReplyDraft(String replyDraft) {
		mReplyDraft = replyDraft;
	}
	
	public SpannableStringBuilder getSSBBody() {
		return mSSBBody;
	}

	public void setSSBBody(SpannableStringBuilder ssbBody) {
		mSSBBody = ssbBody;
	}
}
