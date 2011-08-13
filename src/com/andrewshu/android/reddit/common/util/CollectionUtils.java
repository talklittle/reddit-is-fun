package com.andrewshu.android.reddit.common.util;

import java.util.Collection;

public class CollectionUtils {

	public static boolean isEmpty(Collection<?> theCollection) {
		return theCollection == null || theCollection.isEmpty();
	}

}
