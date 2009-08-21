package com.andrewshu.android.reddit;

public class Thing {
	public ThreadInfo thread = null;
	public CommentInfo comment = null;
//	public SubredditInfo subreddit = null;
	
	public Thing(ThreadInfo thread, CommentInfo comment) {
		this.thread = thread;
		this.comment = comment;
	}
}
