package com.andrewshu.android.reddit.common.util;

import java.util.ArrayList;
import java.util.Iterator;

public class StringUtils {

	public static boolean isEmpty(CharSequence s) {
		return s == null || "".equals(s);
	}

	public static boolean listContainsIgnoreCase(ArrayList<String> list, String str){
		for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
			String string = (String) iterator.next();
			
			if(string.equalsIgnoreCase(str))
				return true;
		}
		return false;
	}

}
