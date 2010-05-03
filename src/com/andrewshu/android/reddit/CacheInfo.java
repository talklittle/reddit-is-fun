/*
 * Copyright 2010 Andrew Shu
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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import android.content.Context;
import android.util.Log;

public class CacheInfo implements Serializable {
	static final long serialVersionUID = 39;
	static final String TAG = "CacheInfo";
	
	
	// timestamps for each cache
	public long subredditTime = 0;
	public long threadTime = 0;
	
	// the ids for the cached JSON objects
	public String subreddit = null;
	public String threadId = null;

	
	
	/**
	 * Copy the contents of an InputStream to cache file, then close the InputStream,
	 * and return a new FileInputStream to the just-written file.
	 * @param context
	 * @param in
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	static FileInputStream writeThenRead(Context context, InputStream in, String filename) throws IOException {
    	FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE);
    	byte[] buf = new byte[1024];
    	int len = 0;
    	long total = 0;  // for debugging
    	while ((len = in.read(buf)) > 0) {
    		fos.write(buf, 0, len);
    		total += len;
    	}
    	if (Constants.LOGGING) Log.d(TAG, total + " bytes written to cache file: " + filename);
    	fos.close();
    	in.close();
    	
    	// return a new InputStream
   		return context.openFileInput(filename);
	}
	
	static boolean checkFreshSubredditCache(Context context) {
    	long time = System.currentTimeMillis();
    	long subredditTime = getCachedSubredditTime(context);
		return time - subredditTime <= Constants.DEFAULT_FRESH_DURATION;
	}
    
    static boolean checkFreshThreadCache(Context context) {
    	long time = System.currentTimeMillis();
    	long threadTime = getCachedThreadTime(context);
		return time - threadTime <= Constants.DEFAULT_FRESH_DURATION;
    }
    
    static void deleteAllCaches(Context context) {
    	for (String fileName : context.fileList()) {
    		context.deleteFile(fileName);
    	}
    }
    
    static CacheInfo getCacheInfo(Context context) throws IOException, ClassNotFoundException {
    	FileInputStream fis = context.openFileInput(Constants.FILENAME_CACHE_INFO);
    	ObjectInputStream ois = new ObjectInputStream(fis);
    	CacheInfo ci = (CacheInfo) ois.readObject();
    	ois.close();
    	fis.close();
    	return ci;
    }
    
    static String getCachedSubreddit(Context context) {
    	try {
    		return getCacheInfo(context).subreddit;
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
    		return null;
    	}
    }
    
    static long getCachedSubredditTime(Context context) {
    	try {
    		return getCacheInfo(context).subredditTime;
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
    		return 0;
    	}
    }

    static String getCachedThreadId(Context context) {
    	try {
    		return getCacheInfo(context).threadId;
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
    		return null;
    	}
    }
    
    static long getCachedThreadTime(Context context) {
    	try {
    		return getCacheInfo(context).threadTime;
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
    		return 0;
    	}
    }
    
    static void invalidateCachedSubreddit(Context context) {
    	CacheInfo ci = null;
    	try {
    		ci = getCacheInfo(context);
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
    	}
		if (ci == null)
			ci = new CacheInfo();
    	
    	try {
	    	FileOutputStream fos = context.openFileOutput(Constants.FILENAME_CACHE_INFO, Context.MODE_PRIVATE);
	    	ObjectOutputStream oos = new ObjectOutputStream(fos);
	    	ci.subreddit = null;
	    	ci.subredditTime = 0;
	    	oos.writeObject(ci);
    	} catch (IOException e) {
    		if (Constants.LOGGING) Log.e(TAG, e.getMessage());
    	}
    }
    
    static void invalidateCachedThreadId(Context context) {
    	CacheInfo ci = null;
    	try {
    		ci = getCacheInfo(context);
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(TAG, "error w/ getCacheInfo:" + e.getMessage());
    	}
		if (ci == null)
			ci = new CacheInfo();
    	
    	try {
	    	FileOutputStream fos = context.openFileOutput(Constants.FILENAME_CACHE_INFO, Context.MODE_PRIVATE);
	    	ObjectOutputStream oos = new ObjectOutputStream(fos);
	    	ci.threadId = null;
	    	ci.threadTime = 0;
	    	oos.writeObject(ci);
    	} catch (IOException e) {
    		if (Constants.LOGGING) Log.e(TAG, "error w/ getCacheInfo:" + e.getMessage());
    	}
    }
    
    static void setCachedSubreddit(Context context, String subreddit) throws IOException {
    	CacheInfo ci = null;
    	try {
    		ci = getCacheInfo(context);
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(TAG, "error w/ getCacheInfo:" + e.getMessage());
    	}
		if (ci == null)
			ci = new CacheInfo();
    	
		FileOutputStream fos = context.openFileOutput(Constants.FILENAME_CACHE_INFO, Context.MODE_PRIVATE);
    	ObjectOutputStream oos = new ObjectOutputStream(fos);
    	ci.subreddit = subreddit;
    	ci.subredditTime = System.currentTimeMillis();
    	oos.writeObject(ci);
    }

    static void setCachedThreadId(Context context, String threadId) throws IOException {
    	CacheInfo ci = null;
    	try {
    		ci = getCacheInfo(context);
    	} catch (Exception e) {
    		if (Constants.LOGGING) Log.e(TAG, "error w/ getCacheInfo:" + e.getMessage());
    	}
		if (ci == null)
			ci = new CacheInfo();

		FileOutputStream fos = context.openFileOutput(Constants.FILENAME_CACHE_INFO, Context.MODE_PRIVATE);
    	ObjectOutputStream oos = new ObjectOutputStream(fos);
    	ci.threadId = threadId;
    	ci.threadTime = System.currentTimeMillis();
    	oos.writeObject(ci);
    }
}
