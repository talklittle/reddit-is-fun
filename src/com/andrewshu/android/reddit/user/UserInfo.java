package com.andrewshu.android.reddit.user;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class UserInfo implements Serializable, Parcelable {
	
	private static final long serialVersionUID = 1L;
	
	private Boolean has_mail;
	private String name;
	private long created;
	private String modhash;
	private long created_utc;
	private int link_karma;
	private int comment_karma;
	private boolean is_gold;
	private boolean is_mod;
	private String id;
	private Boolean has_mod_mail;
	
	public UserInfo() {
		super();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	/**
	 * Use dest.writeValue for fields that may be null
	 */
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeValue(has_mail);
		dest.writeString(name);
		dest.writeLong(created);
		dest.writeValue(modhash);
		dest.writeLong(created_utc);
		dest.writeInt(link_karma);
		dest.writeInt(comment_karma);
		dest.writeString(id);
		dest.writeValue(has_mod_mail);
		
		boolean booleans[] = new boolean[2];
		booleans[0] = is_gold;
		booleans[1] = is_mod;
		dest.writeBooleanArray(booleans);
	}
	
	public static final Parcelable.Creator<UserInfo> CREATOR = new Parcelable.Creator<UserInfo>() {
		public UserInfo createFromParcel(Parcel in) {
		    return new UserInfo(in);
		}
		
		public UserInfo[] newArray(int size) {
		    return new UserInfo[size];
		}
	};
		
	private UserInfo(Parcel in) {
		has_mail      = (Boolean) in.readValue(null);
		name          = in.readString();
		created       = in.readLong();
		modhash       = (String) in.readValue(null);
		created_utc   = in.readLong();
		link_karma    = in.readInt();
		comment_karma = in.readInt();
		id            = in.readString();
		has_mod_mail  = (Boolean) in.readValue(null);
		
		boolean booleans[] = new boolean[2];
		in.readBooleanArray(booleans);
		is_gold = booleans[0];
		is_mod  = booleans[1];
	}

	public boolean isHas_mail() {
		return has_mail;
	}

	public void setHas_mail(boolean has_mail) {
		this.has_mail = has_mail;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getCreated() {
		return created;
	}

	public void setCreated(long created) {
		this.created = created;
	}

	public String getModhash() {
		return modhash;
	}

	public void setModhash(String modhash) {
		this.modhash = modhash;
	}

	public long getCreated_utc() {
		return created_utc;
	}

	public void setCreated_utc(long created_utc) {
		this.created_utc = created_utc;
	}

	public int getLink_karma() {
		return link_karma;
	}

	public void setLink_karma(int link_karma) {
		this.link_karma = link_karma;
	}

	public int getComment_karma() {
		return comment_karma;
	}

	public void setComment_karma(int comment_karma) {
		this.comment_karma = comment_karma;
	}

	public boolean isIs_gold() {
		return is_gold;
	}

	public void setIs_gold(boolean is_gold) {
		this.is_gold = is_gold;
	}

	public boolean isIs_mod() {
		return is_mod;
	}

	public void setIs_mod(boolean is_mod) {
		this.is_mod = is_mod;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public boolean isHas_mod_mail() {
		return has_mod_mail;
	}

	public void setHas_mod_mail(boolean has_mod_mail) {
		this.has_mod_mail = has_mod_mail;
	}
	
}
