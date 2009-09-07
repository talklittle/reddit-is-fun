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
	
	public String mReplyDraft = null;
	
	// TODO?: Make setters for everything instead... or not.
	public void put(String key, String value) {
		mValues.put(key, value);
	}
	
	public void setReplyDraft(String replyDraft) {
		mReplyDraft = replyDraft;
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
	
	public String getReplyDraft() {
		return mReplyDraft;
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
