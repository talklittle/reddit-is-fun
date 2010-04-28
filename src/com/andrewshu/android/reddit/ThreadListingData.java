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

public class ThreadListingData {
	private ThreadListingDataListing[] children;
	private String after;
	private String before;
	private String modhash;
	
	public String getAfter() {
		return after;
	}
	public String getBefore() {
		return before;
	}
	public String getModhash() {
		return modhash;
	}
	public ThreadListingDataListing[] getChildren() {
		return children;
	}
}