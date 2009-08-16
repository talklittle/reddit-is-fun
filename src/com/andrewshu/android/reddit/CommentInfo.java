package com.andrewshu.android.reddit;

import java.util.HashMap;

import org.codehaus.jackson.JsonNode;

/**
 * Class representing a single comment in reddit API.
 * 
 * @author TalkLittle
 *
 */
public class CommentInfo {
	public static final String AUTHOR       = "author";
	public static final String BODY         = "body";
	public static final String BODY_HTML    = "body_html";
	public static final String CREATED      = "created";
	public static final String CREATED_UTC  = "created_utc";
	public static final String DOWNS        = "downs";
	public static final String ID           = "id";
	public static final String LIKES        = "likes";
	public static final String LINK_ID      = "link_id";
	public static final String NAME         = "name"; // thing fullname
	public static final String REPLIES      = "replies";
	public static final String PARENT_ID    = "parent_id";
	public static final String SR_ID        = "sr_id";
	public static final String UPS          = "ups";
	
	public static final String[] _KEYS = {
		AUTHOR, BODY, BODY_HTML, CREATED, CREATED_UTC, DOWNS, ID, LIKES, LINK_ID, NAME, PARENT_ID, REPLIES, SR_ID, UPS
	};
	// Only need to save the keys we actually use.
	public static final String[] SAVE_KEYS = {
		AUTHOR, BODY, DOWNS, ID, LIKES, NAME, PARENT_ID, UPS
	};
	
	public HashMap<String, String> mValues = new HashMap<String, String>();
	public JsonNode mReplies;  // Unused. use another JSON GET if necessary
	public ThreadInfo mOpInfo = null;
	public int mIndent = 0;
	public Integer mListOrder = 0;
	public String mReplyDraft = null;
	
	public CommentInfo() {
		// Do nothing.
	}
	
	public CommentInfo(String author, String body, String bodyHtml, String created, String createdUtc,
			String downs, String id, String likes, String linkId, String name, String parentId, String srId, String ups) {
		if (author != null)
			put(AUTHOR, author);
		if (body != null)
			put(BODY, body);
		if (bodyHtml != null)
			put(BODY_HTML, bodyHtml);
		if (created != null)
			put(CREATED, created);
		if (createdUtc != null)
			put(CREATED_UTC, createdUtc);
		if (downs != null)
			put(DOWNS, downs);
		if (id != null)
			put(ID, id);
		if (likes != null)
			put(LIKES, likes);
		if (linkId != null)
			put(LINK_ID, linkId);
		if (name != null)
			put(NAME, name);
		if (parentId != null)
			put(PARENT_ID, parentId);
		if (srId != null)
			put(SR_ID, srId);
		if (ups != null)
			put(UPS, ups);
	}

	public void setOpInfo(ThreadInfo opInfo) {
		mOpInfo = opInfo;
	}
	
	public void setIndent(int indent) {
		mIndent = indent;
	}
	
	public void setListOrder(int listOrder) {
		mListOrder = listOrder;
	}
	
	public void setReplyDraft(String replyDraft) {
		mReplyDraft = replyDraft;
	}
	
	// TODO?: Make setters for everything instead... or not.
	public void put(String key, String value) {
		mValues.put(key, value);
	}
	
	public void setDowns(String downs) {
		mValues.put(DOWNS, downs);
	}
	
	public void setLikes(String likes) {
		mValues.put(LIKES, likes);
	}
	
	public void setUps(String ups) {
		mValues.put(UPS, ups);
	}
	
	public String getAuthor() {
		return mValues.get(AUTHOR);
	}
	
	public String getBody() {
		return mValues.get(BODY);
	}
	
	public String getCreatedUtc() {
		return mValues.get(CREATED_UTC);
	}
	
	public String getDowns() {
		return mValues.get(DOWNS);
	}
	
	public String getId() {
		return mValues.get(ID);
	}
	
	public int getIndent() {
		return mIndent;
	}
	
	public String getLikes() {
		return mValues.get(LIKES);
	}
	
	public Integer getListOrder() {
		return mListOrder;
	}
	
	public String getName() {
		return mValues.get(NAME);
	}
	
	public ThreadInfo getOP() {
		return mOpInfo;
	}
	
	public String getReplyDraft() {
		return mReplyDraft;
	}

	public String getSubmissionTime() {
		return mValues.get(CREATED);
	}

	public String getUps() {
		return mValues.get(UPS);
	}
	
	
}
