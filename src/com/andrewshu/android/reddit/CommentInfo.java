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
	public static final String AUTHOR      = "author";
	public static final String BODY        = "body";
	public static final String BODY_HTML   = "body_html";
	public static final String CREATED     = "created";
	public static final String CREATED_UTC = "created_utc";
	public static final String DOWNS       = "downs";
	public static final String ID          = "id";
	public static final String LIKES       = "likes";
	public static final String LINK_ID     = "link_id";
	public static final String NAME        = "name"; // thing fullname
	public static final String PARENT_ID   = "parent_id";
	public static final String UPS         = "ups";
	public static JsonNode REPLIES;  // not stored here. use another JSON GET if necessary
	
	public static final String[] _KEYS = {
		AUTHOR, BODY, BODY_HTML, CREATED, CREATED_UTC, DOWNS, ID, LIKES, LINK_ID, NAME, PARENT_ID, UPS
	};
	
	public HashMap<String, String> mValues = new HashMap<String, String>();
	public ThreadInfo mOpInfo = null;
	public int mIndent = 0;
	public Integer mListOrder = 0;

	public void setOpInfo(ThreadInfo opInfo) {
		mOpInfo = opInfo;
	}
	
	public void setIndent(int indent) {
		mIndent = indent;
	}
	
	public void setListOrder(int listOrder) {
		mListOrder = listOrder;
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

	public String getSubmissionTime() {
		return mValues.get(CREATED);
	}

	public String getUps() {
		return mValues.get(UPS);
	}
	
	
}
