package com.andrewshu.android.reddit.user;


public class UserListing {
	private String kind;
	private UserInfo data;
	
	public UserListing() {}
	
	public void setKind(String kind) {
		this.kind = kind;
	}
	public String getKind() {
		return kind;
	}
	
	public void setData(UserInfo data) {
		this.data = data;
	}
	public UserInfo getData() {
		return data;
	}
}
