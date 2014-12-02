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

package com.andrewshu.android.reddit.things;

import java.io.Serializable;
import java.util.ArrayList;

import org.codehaus.jackson.annotate.JsonAnySetter;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Html;
import android.text.SpannableString;

import com.andrewshu.android.reddit.markdown.MarkdownURL;

/**
 * Class representing a thread posting in reddit API.
 * 
 * @author TalkLittle
 *
 */
public class ThingInfo implements Serializable, Parcelable {
	static final long serialVersionUID = 40;
	
	// thread: t
	// comment: c
	// message: m
	
	private String author;					// t c m
	private String body;					//   c m
	private String body_html;				//   c m
	private boolean clicked;				// t
	private String context;					//     m
	private double created;					// t c m
	private double created_utc;				// t c m
	private String dest;					//     m
	private String domain;					// t
	private int downs;						// t c
	private Long first_message;				//     m
	private boolean hidden;					// t
	private String id;						// t c m
	private boolean is_self;				// t
	private Boolean likes;					// t c
	private String link_id;					//   c
//	private MediaInfo media;				// t		// TODO
//	private MediaEmbedInfo media_embed;		// t		// TODO
	private String name;					// t c m
	private boolean new_;					//     m
	private int num_comments;				// t
	private boolean over_18;				// t
	private String parent_id;				//   c m
	private String permalink;				// t
	private Listing replies;				//   c
	private boolean saved;					// t
	private int score;						// t
	private String selftext;				// t
	private String selftext_html;			// t
	private String subject;					//     m
	private String subreddit;				// t c
	private String subreddit_id;			// t
	private String thumbnail;				// t
	private String title;					// t
	private int ups;						// t c
	private String url;						// t
	private boolean was_comment;			//     m
	
	private final ArrayList<MarkdownURL> mUrls = new ArrayList<MarkdownURL>();
	transient private CharSequence mSpannedSelftext = null;
	transient private CharSequence mSpannedBody = null;
	transient private SpannableString mSSAuthor = null;
	
	transient private Bitmap mThumbnailBitmap = null;
	transient private Integer mThumbnailResource = null;
	
	private int mIndent = 0;
	private String mReplyDraft = null;
	private boolean mIsLoadMoreCommentsPlaceholder = false;
	private boolean mIsHiddenCommentHead = false;
	private boolean mIsHiddenCommentDescendant = false;

	public ThingInfo() {
		super();
	}
	
	public String getAuthor() {
		return author;
	}
	
	public String getBody() {
		return body;
	}

	public String getBody_html() {
		return body_html;
	}

	public String getContext() {
		return context;
	}

	public double getCreated() {
		return created;
	}

	public double getCreated_utc() {
		return created_utc;
	}

	public String getDest() {
		return dest;
	}

	public String getDomain() {
		return domain;
	}

	public int getDowns() {
		return downs;
	}

	public Long getFirst_message() {
		return first_message;
	}

	public String getId() {
		return id;
	}

	public int getIndent() {
		return mIndent;
	}

	public Boolean getLikes() {
		return likes;
	}

	public String getLink_id() {
		return link_id;
	}
	
	public String getName() {
		return name;
	}

	public int getNum_comments() {
		return num_comments;
	}

	public String getParent_id() {
		return parent_id;
	}

	public String getPermalink() {
		return permalink;
	}

	public Listing getReplies() {
		return replies;
	}

	public String getReplyDraft() {
		return mReplyDraft;
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

	public CharSequence getSpannedBody() {
		return mSpannedBody;
	}

	public CharSequence getSpannedSelftext() {
		return mSpannedSelftext;
	}

	public SpannableString getSSAuthor() {
		return mSSAuthor;
	}
	
	public String getSubject() {
		return subject;
	}

	public String getSubreddit() {
		return subreddit;
	}

	public String getSubreddit_id() {
		return subreddit_id;
	}

	public String getThumbnail() {
		return thumbnail;
	}

	public Bitmap getThumbnailBitmap() {
		return mThumbnailBitmap;
	}
	
	public Integer getThumbnailResource() {
		return mThumbnailResource;
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

	public ArrayList<MarkdownURL> getUrls() {
		return mUrls;
	}

	@JsonAnySetter
	public void handleUnknown(String key, Object value) {
		// Ignore.
	}

	public boolean isClicked() {
		return clicked;
	}

	public boolean isHidden() {
		return hidden;
	}

	public boolean isIs_self() {
		return is_self;
	}

	public boolean isNew() {
		return new_;
	}

	public boolean isOver_18() {
		return over_18;
	}

	public boolean isSaved() {
		return saved;
	}

	public boolean isWas_comment() {
		return was_comment;
	}
	
	public boolean isLoadMoreCommentsPlaceholder() {
		return mIsLoadMoreCommentsPlaceholder;
	}
	
	public boolean isHiddenCommentHead() {
		return mIsHiddenCommentHead;
	}
	
	public boolean isHiddenCommentDescendant() {
		return mIsHiddenCommentDescendant;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public void setBody_html(String body_html) {
		this.body_html = body_html;
	}

	public void setClicked(boolean clicked) {
		this.clicked = clicked;
	}

	public void setContext(String context) {
		this.context = context;
	}

	public void setCreated(double created) {
		this.created = created;
	}

	public void setCreated_utc(double created_utc) {
		this.created_utc = created_utc;
	}

	public void setDest(String dest) {
		this.dest = dest;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public void setDowns(int downs) {
		this.downs = downs;
	}

	public void setFirst_message(Long first_message) {
		this.first_message = first_message;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}
	
	public void setHiddenCommentHead(boolean hiddenCommentHead) {
		this.mIsHiddenCommentHead = hiddenCommentHead;
	}
	
	public void setHiddenCommentDescendant(boolean hiddenCommentDescendant) {
		this.mIsHiddenCommentDescendant = hiddenCommentDescendant;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setIndent(int indent) {
		mIndent = indent;
	}

	public void setIs_self(boolean is_self) {
		this.is_self = is_self;
	}

	public void setLikes(Boolean likes) {
		this.likes = likes;
	}

	public void setLink_id(String link_id) {
		this.link_id = link_id;
	}
	
	public void setLoadMoreCommentsPlaceholder(boolean loadMoreCommentsPlaceholder) {
		mIsLoadMoreCommentsPlaceholder = loadMoreCommentsPlaceholder;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setNew(boolean new_) {
		this.new_ = new_;
	}

	public void setNum_comments(int num_comments) {
		this.num_comments = num_comments;
	}

	public void setOver_18(boolean over_18) {
		this.over_18 = over_18;
	}

	public void setParent_id(String parent_id) {
		this.parent_id = parent_id;
	}

	public void setPermalink(String permalink) {
		this.permalink = permalink;
	}
	
	public void setReplies(Listing replies) {
		this.replies = replies;
	}
	
	public void setReplyDraft(String replyDraft) {
		mReplyDraft = replyDraft;
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
	
	public void setSpannedBody(CharSequence ssbBody) {
		mSpannedBody = ssbBody;
	}

	public void setSpannedSelftext(CharSequence selftext) {
		mSpannedSelftext = selftext;
	}

	public void setSSAuthor(SpannableString ssAuthor) {
		mSSAuthor = ssAuthor;
	}
	
	public void setSubject(String subject) {
		this.subject = subject;
	}

	public void setSubreddit(String subreddit) {
		this.subreddit = subreddit;
	}

	public void setSubreddit_id(String subreddit_id) {
		this.subreddit_id = subreddit_id;
	}

	public void setThumbnail(String thumbnail) {
		this.thumbnail = thumbnail;
	}

	public void setThumbnailBitmap(Bitmap thumbnailBitmap) {
		this.mThumbnailBitmap = thumbnailBitmap;
	}
	
	public void setThumbnailResource(Integer thumbnailResource) {
		this.mThumbnailResource = thumbnailResource;
	}

	public void setTitle(String title) {
		this.title = Html.fromHtml(title).toString();
	}

	public void setUps(int ups) {
		this.ups = ups;
	}

	public void setUrl(String url) {
		this.url = Html.fromHtml(url).toString();
	}

	public void setWas_comment(boolean was_comment) {
		this.was_comment = was_comment;
	}

	//Parcelable interface
	//We are using write/read value for non primitives to support nulls

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeValue(author);
		out.writeValue(body);
		out.writeValue(body_html);
		out.writeValue(context);
		out.writeDouble(created);
		out.writeDouble(created_utc);
		out.writeValue(dest);
		out.writeValue(domain);
		out.writeInt(downs);
		out.writeValue(first_message);
		out.writeValue(id);
		out.writeValue(link_id);
		out.writeValue(name);
		out.writeInt(num_comments);
		out.writeValue(parent_id);
		out.writeValue(permalink);
		out.writeInt(score);
		out.writeValue(selftext);
		out.writeValue(selftext_html);
		out.writeValue(subject);
		out.writeValue(subreddit);
		out.writeValue(subreddit_id);
		out.writeValue(thumbnail);
		out.writeValue(title);
		out.writeInt(ups);
		out.writeValue(url);
		out.writeValue(likes);

		boolean booleans[] = new boolean[10];
		booleans[0] = clicked;
		booleans[1] = hidden;
		booleans[2] = is_self;
		booleans[3] = new_;
		booleans[4] = over_18;
		booleans[5] = saved;
		booleans[6] = was_comment;
		booleans[7] = mIsLoadMoreCommentsPlaceholder;
		booleans[8] = mIsHiddenCommentHead;
		booleans[9] = mIsHiddenCommentDescendant;
		out.writeBooleanArray(booleans);
	}

	private ThingInfo(Parcel in) {
		author        = (String) in.readValue(null);
		body          = (String) in.readValue(null);
		body_html     = (String) in.readValue(null);
		context       = (String) in.readValue(null);
		created       = in.readDouble();
		created_utc   = in.readDouble();
		dest          = (String) in.readValue(null);
		domain        = (String) in.readValue(null);
		downs         = in.readInt();
		first_message = (Long) in.readValue(null);
		id            = (String) in.readValue(null);
		link_id       = (String) in.readValue(null);
		name          = (String) in.readValue(null);
		num_comments  = in.readInt();
		parent_id     = (String) in.readValue(null);
		permalink     = (String) in.readValue(null);
		score         = in.readInt();
		selftext      = (String) in.readValue(null);
		selftext_html = (String) in.readValue(null);
		subject       = (String) in.readValue(null);
		subreddit     = (String) in.readValue(null);
		subreddit_id  = (String) in.readValue(null);
		thumbnail     = (String) in.readValue(null);
		title         = (String) in.readValue(null);
		ups           = in.readInt();
		url           = (String) in.readValue(null);
		likes         = (Boolean) in.readValue(null);

		boolean booleans[] = new boolean[10];
		in.readBooleanArray(booleans);
		clicked                        = booleans[0];
		hidden                         = booleans[1];
		is_self                        = booleans[2];
		new_                           = booleans[3];
		over_18                        = booleans[4];
		saved                          = booleans[5];
		was_comment                    = booleans[6];
		mIsLoadMoreCommentsPlaceholder = booleans[7];
		mIsHiddenCommentHead           = booleans[8];
		mIsHiddenCommentDescendant     = booleans[9];
	}

	public static final Parcelable.Creator<ThingInfo> CREATOR
		= new  Parcelable.Creator<ThingInfo>() {
		public ThingInfo createFromParcel(Parcel in) {
			return new ThingInfo(in);
		}

		public ThingInfo[] newArray(int size){
			return new ThingInfo[size];
		}
	};

}
