package com.andrewshu.android.reddit;

import java.util.HashMap;

/**
 * Class representing a single comment in reddit API.
 * 
 * @author TalkLittle
 *
 */
public class MessageInfo {
	public static final String AUTHOR       = "author";
	public static final String BODY         = "body";
	public static final String BODY_HTML    = "body_html";
	public static final String CONTEXT      = "context";
	public static final String CREATED      = "created";
	public static final String CREATED_UTC  = "created_utc";
	public static final String DEST         = "dest";
	public static final String ID           = "id";
	public static final String NAME         = "name"; // thing fullname
	public static final String NEW          = "new";
	public static final String SUBJECT      = "subject";
	public static final String WAS_COMMENT  = "was_comment";
	
	public static final String[] _KEYS = {
		AUTHOR, BODY, BODY_HTML, CONTEXT, CREATED, CREATED_UTC, DEST, ID, NAME, NEW, SUBJECT, WAS_COMMENT
	};
	
	public HashMap<String, String> mValues = new HashMap<String, String>();
	
	// TODO?: Make setters for everything instead... or not.
	public void put(String key, String value) {
		mValues.put(key, value);
	}
	
	public String getAuthor() {
		return mValues.get(AUTHOR);
	}
	
	public String getBody() {
		return mValues.get(BODY);
	}
	
	public String getContext() {
		return mValues.get(CONTEXT);
	}
	
	public String getCreatedUtc() {
		return mValues.get(CREATED_UTC);
	}
	
	public String getDest() {
		return mValues.get(DEST);
	}
	
	public String getId() {
		return mValues.get(ID);
	}
	
	public String getName() {
		return mValues.get(NAME);
	}
	
	public String getNew() {
		return mValues.get(NEW);
	}
	
	public String getSubject() {
		return mValues.get(SUBJECT);
	}
	
	public String getSubmissionTime() {
		return mValues.get(CREATED);
	}
	
	public String getWasComment() {
		return mValues.get(WAS_COMMENT);
	}

}
