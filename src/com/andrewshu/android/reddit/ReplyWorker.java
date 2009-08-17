package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;

import android.widget.Toast;

public class ReplyWorker extends Thread{
	
	private static final String TAG = "ReplyWorker";
	
	private CharSequence _mParentThingId, _mText;
	CommentInfo _mTargetCommentInfo;
	RedditSettings _mSettings;
	RedditCommentsListActivity.CommentInserter _mCallback;
	
	public ReplyWorker(CharSequence parentThingId, CharSequence text, CommentInfo targetCommentInfo,
			RedditSettings settings, RedditCommentsListActivity.CommentInserter callback) {
		_mParentThingId = parentThingId;
		_mText = text;
		_mTargetCommentInfo = targetCommentInfo;
		_mSettings = settings;
		_mCallback = callback;
	}
	
    public void run() {
    	CommentInfo newlyCreatedComment = null;
    	String userError = "Error replying. Please try again.";
    	
    	String status = "";
    	if (!_mSettings.loggedIn) {
    		_mSettings.handler.post(new ErrorToaster("You must be logged in to reply.", Toast.LENGTH_LONG, _mSettings));
    		return;
    	}
    	// Update the modhash if necessary
    	if (_mSettings.modhash == null) {
    		_mSettings.setModhash(Common.doUpdateModhash(_mSettings));
    		if (_mSettings.modhash == null) {
    			// doUpdateModhash should have given an error about credentials
    			throw new RuntimeException("Reply failed because doUpdateModhash() failed");
    		}
    	}
    	
    	try {
    		// Create a new HttpClient with new timeout, and copy cookies over from the main one
    		DefaultHttpClient client = new DefaultHttpClient();
    		client.getParams().setParameter(HttpConnectionParams.SO_TIMEOUT, 30000);
    		List<Cookie> mainCookies = _mSettings.client.getCookieStore().getCookies();
    		for (Cookie c : mainCookies) {
    			client.getCookieStore().addCookie(c);
    		}
    		
    		// Construct data
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("thing_id", _mParentThingId.toString()));
			nvps.add(new BasicNameValuePair("text", _mText.toString()));
			nvps.add(new BasicNameValuePair("r", _mSettings.subreddit.toString()));
			nvps.add(new BasicNameValuePair("uh", _mSettings.modhash.toString()));
			// Votehash is currently unused by reddit 
//				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
			
			HttpPost httppost = new HttpPost("http://www.reddit.com/api/comment");
	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
	        
	        Log.d(TAG, nvps.toString());
	        
            // Perform the HTTP POST request
	    	HttpResponse response = _mSettings.client.execute(httppost);
	    	status = response.getStatusLine().toString();
        	if (!status.contains("OK"))
        		throw new HttpException(status);
        	
        	HttpEntity entity = response.getEntity();

        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	String line = in.readLine();
        	if (line == null) {
        		throw new HttpException("No content returned from reply POST");
        	}
        	if (line.contains("WRONG_PASSWORD")) {
        		throw new Exception("Wrong password");
        	}
        	if (line.contains("USER_REQUIRED")) {
        		// The modhash probably expired
        		_mSettings.setModhash(null);
        		_mSettings.handler.post(new ErrorToaster("Error submitting reply. Please try again.", Toast.LENGTH_LONG, _mSettings));
        		return;
        	}
        	
        	Log.d(TAG, line);

//        	// DEBUG
//        	int c;
//        	boolean done = false;
//        	StringBuilder sb = new StringBuilder();
//        	for (int k = 0; k < line.length(); k += 80) {
//        		for (int i = 0; i < 80; i++) {
//        			if (k + i >= line.length()) {
//        				done = true;
//        				break;
//        			}
//        			c = line.charAt(k + i);
//        			sb.append((char) c);
//        		}
//        		Log.d(TAG, "doReply response content: " + sb.toString());
//        		sb = new StringBuilder();
//        		if (done)
//        			break;
//        	}
//	        	

        	String newId, newFullname;
        	Matcher idMatcher = Constants.NEW_ID_PATTERN.matcher(line);
        	if (idMatcher.find()) {
        		newFullname = idMatcher.group(1);
        		newId = idMatcher.group(3);
        	} else {
        		if (line.contains("RATELIMIT")) {
            		// Try to find the # of minutes using regex
                	Matcher rateMatcher = Constants.RATELIMIT_RETRY_PATTERN.matcher(line);
                	if (rateMatcher.find())
                		userError = rateMatcher.group(1);
                	else
                		userError = "you are trying to submit too fast. try again in a few minutes.";
            		throw new Exception(userError);
            	}
            	throw new Exception("No id returned by reply POST.");
        	}
        	
        	in.close();
        	if (entity != null)
        		entity.consumeContent();
        	
        	// Getting here means success. Create a new CommentInfo.
        	newlyCreatedComment = new CommentInfo(
        			_mSettings.username.toString(),     /* author */
        			_mText.toString(),          /* body */
        			null,                     /* body_html */
        			null,                     /* created */
        			String.valueOf(System.currentTimeMillis()), /* created_utc */
        			"0",                      /* downs */
        			newId,                    /* id */
        			Constants.TRUE_STRING,              /* likes */
        			null,                     /* link_id */
        			newFullname,              /* name */
        			_mParentThingId.toString(), /* parent_id */
        			null,                     /* sr_id */
        			"1"                       /* ups */
        			);
        	newlyCreatedComment.setListOrder(_mTargetCommentInfo.getListOrder()+1);
        	if (_mTargetCommentInfo.getListOrder() == 0)
        		newlyCreatedComment.setIndent(0);
        	else
        		newlyCreatedComment.setIndent(_mTargetCommentInfo.getIndent()+1);
        	
    	} catch (Exception e) {
            Log.e(TAG, e.getMessage());
            if (_mSettings.isAlive)
            	_mSettings.handler.post(new ErrorToaster(userError, Toast.LENGTH_LONG, _mSettings));
    	}
    	Log.d(TAG, status);
    	
    	// Post back to UI thread with newly created comment (or null)
    	if (_mSettings.isAlive) {
    		_mCallback.setComment(newlyCreatedComment);
    		_mSettings.handler.post(_mCallback);
    	}
    }	
}
