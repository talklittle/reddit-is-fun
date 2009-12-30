package com.andrewshu.android.reddit;

import java.io.Serializable;
import java.util.List;

import android.content.Context;
import android.widget.ArrayAdapter;

public class SerializableCommentInfoArrayAdapter extends ArrayAdapter<CommentInfo> implements Serializable {
	
	static final long serialVersionUID = 30; 

	public SerializableCommentInfoArrayAdapter(Context context,
			int textViewResourceId, CommentInfo[] objects) {
		super(context, textViewResourceId, objects);
		// TODO Auto-generated constructor stub
	}

	public SerializableCommentInfoArrayAdapter(Context context, int resource,
			int textViewResourceId, CommentInfo[] objects) {
		super(context, resource, textViewResourceId, objects);
		// TODO Auto-generated constructor stub
	}

	public SerializableCommentInfoArrayAdapter(Context context, int resource,
			int textViewResourceId, List<CommentInfo> objects) {
		super(context, resource, textViewResourceId, objects);
		// TODO Auto-generated constructor stub
	}

	public SerializableCommentInfoArrayAdapter(Context context, int resource,
			int textViewResourceId) {
		super(context, resource, textViewResourceId);
		// TODO Auto-generated constructor stub
	}

	public SerializableCommentInfoArrayAdapter(Context context,
			int textViewResourceId, List<CommentInfo> objects) {
		super(context, textViewResourceId, objects);
		// TODO Auto-generated constructor stub
	}

	public SerializableCommentInfoArrayAdapter(Context context,
			int textViewResourceId) {
		super(context, textViewResourceId);
		// TODO Auto-generated constructor stub
	}

}
