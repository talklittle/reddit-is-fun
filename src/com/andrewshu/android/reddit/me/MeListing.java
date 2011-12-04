package com.andrewshu.android.reddit.me;


public class MeListing {
	private String kind;
	private MeInfo data;
	
	public MeListing() {}
	
	public void setKind(String kind) {
		this.kind = kind;
	}
	public String getKind() {
		return kind;
	}
	
	public void setData(MeInfo data) {
		this.data = data;
	}
	public MeInfo getData() {
		return data;
	}
}
