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
package com.andrewshu.android.reddit.markdown;

import java.io.Serializable;

public class MarkdownURL implements Comparable<MarkdownURL>, Serializable {
	static final long serialVersionUID = 29;
	
	public int startOffset;
	public String url;
	public String anchorText;
	
	public MarkdownURL(int startOffset, String url, String anchorText) {
		this.startOffset = startOffset;
		this.url = url;
		this.anchorText = anchorText;
	}
	
	public int compareTo(MarkdownURL other) {
		if (startOffset < other.startOffset)
			return -1;
		else if (startOffset > other.startOffset)
			return 1;
		return 0;
	}
}
