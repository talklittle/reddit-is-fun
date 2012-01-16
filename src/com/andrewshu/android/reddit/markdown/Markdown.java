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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

import com.andrewshu.android.reddit.common.Constants;
import com.andrewshu.android.reddit.common.util.Util;

import dk.brics.automaton.AutomatonMatcher;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

/**
  	This class taken and adapted from MarkdownJ. See lib/MarkdownJ.txt for license.

  	Original MarkdownJ copyright notice follows:
  	
	Copyright (c) 2005, Martian Software
	Authors: Pete Bevin, John Mutchek
	http://www.martiansoftware.com/markdownj
	 
	All rights reserved.
 */
public class Markdown {
//    private Random rnd = new Random();
//    private static final MarkdownCharacterProtector CHAR_PROTECTOR = new MarkdownCharacterProtector();
	
	static final String TAG = "Markdown";
    
    static final RunAutomaton inlineLinkAutomaton = new RunAutomaton(new RegExp("(" + // Whole match = $1
            "\\[([^\\]]*)\\]" + // Link text = $2
            "\\(" +
            "([^\\)\\\\]|(\\\\.))+" +  // 3 cases: 1. simple URL. 2. escaped right-paren in URL. 3. escaped anything in URL. 
            "\\)" +
            ")", RegExp.NONE).toAutomaton());
    // Use inlineLinkAutomaton to check for whole match, then use inlineLink to capture groups
    static final Pattern inlineLink = Pattern.compile("(" + // Whole match = $1
            "\\[(.*?)\\]" + // Link text = $2
            "\\(" +
            "[ \\t]*" +
            "<?(.*?)>?" + // href = $3
            "[ \\t]*" +
            "(" +
            "(['\"])" + // Quote character = $5
            "(.*?)" + // Title = $6
            "\\5" +
            ")?" +
            "\\)" +
            ")", Pattern.DOTALL);
    
    static final RunAutomaton autoLinkUrlAutomaton = new RunAutomaton(new RegExp("((https?|ftp):([^'\"> \t\r\n])+)", RegExp.NONE).toAutomaton());
//    static final Pattern autoLinkEmail = Pattern.compile("<([-.\\w]+\\@[-a-z0-9]+(\\.[-a-z0-9]+)*\\.[a-z]+)>");
    
    static final RunAutomaton subredditAutomaton = new RunAutomaton(new RegExp("/[rR]/[a-zA-Z0-9]+/?", RegExp.NONE).toAutomaton());
	
    /**
     * @param txt input
     * @param urls out URLs
     */
    public void getURLs(String txt, ArrayList<MarkdownURL> urls) {
    	if (txt == null) {
    		txt = "";
    	}
        // Standardize line endings:
        txt.replaceAll("\\r\\n", "\n"); // DOS to Unix
        txt.replaceAll("\\r", "\n"); // Mac to Unix
        txt.replaceAll("^[ \\t]+$", "");
 
//        // Make sure $text ends with a couple of newlines:
//        text.append("\n\n");
 
//        text.detabify();
        txt.replaceAll("^[ ]+$", "");
        
        urls.clear();
        
        TreeMap<Integer, Integer> startToEndOffsetMap = new TreeMap<Integer, Integer>();
        
        // doAnchors originally called from runBlockGamut -> formParagraphs -> runSpanGamut 
        txt = doAnchorURLs(txt, urls, startToEndOffsetMap);
        txt = doAutoLinkURLs(txt, urls, startToEndOffsetMap);
        txt = doAutoLinkSubredditURLs(txt, urls, startToEndOffsetMap);
        
        startToEndOffsetMap.clear();
        
        Collections.sort(urls);
    }
    
    /**
	* Perform the conversion from Markdown to HTML.
	*
	* @param txt - input in markdown format
	* @return HTML block corresponding to txt passed in.
	*/
    @Deprecated
    public SpannableStringBuilder markdown(String txt, SpannableStringBuilder ssb, ArrayList<MarkdownURL> urls) {
    	if (txt == null) {
            txt = "";
        }
        
        // Standardize line endings:
        txt.replaceAll("\\r\\n", "\n"); // DOS to Unix
        txt.replaceAll("\\r", "\n"); // Mac to Unix
        txt.replaceAll("^[ \\t]+$", "");
 
//        // Make sure $text ends with a couple of newlines:
//        text.append("\n\n");
 
//        text.detabify();
        txt.replaceAll("^[ ]+$", "");
        
        ssb.append(txt);
        // doAnchors originally called from runBlockGamut -> formParagraphs -> runSpanGamut 
        urls.clear();
        doAnchors(ssb, urls);
        doAutoLinks(ssb, urls);
        
//	        hashHTMLBlocks(text);
//	        stripLinkDefinitions(text);
//	        text = runBlockGamut(text);
//	        unEscapeSpecialChars(text);
 
//	        text.append("\n");
        
        Collections.sort(urls);
        return ssb;
    }
    
    private boolean isOverlapping(int start, int end, TreeMap<Integer, Integer> startToEndOffsetMap) {
    	for (Map.Entry<Integer, Integer> startEnd : startToEndOffsetMap.entrySet()) {
    		int entryStart = startEnd.getKey();
    		int entryEnd = startEnd.getValue();
    		if (entryStart <= start && entryEnd > start)
    			return true;
    		else if (entryStart >= start && entryEnd <= end)
    			return true;
    		else if (entryStart >= end)
    			return false;
    	}
    	return false;
    }
    
    private void saveStartAndEnd(int start, int end, TreeMap<Integer, Integer> startToEndOffsetMap) {
    	startToEndOffsetMap.put(start, end);
    }
    
    /**
     * @param txt input text
     * @param urls Out URLs from anchors
     * @return updated text with anchors replaced
     */
    private String doAnchorURLs(String txt, ArrayList<MarkdownURL> urls, TreeMap<Integer, Integer> startToEndOffsetMap) {
    	// Inline-style links: [link text](url "optional title")
        AutomatonMatcher am = inlineLinkAutomaton.newMatcher(txt);
        // The offset into the entire original string 
        int start = 0;
        while (am.find()) {
        	// The offsets from start (offset into orig. string = start + anchorStart)
        	int anchorStart = am.start();
        	int anchorEnd = am.end();
        	Matcher m = inlineLink.matcher(am.group());
        	if (!m.find())
        		continue;
        	String linkText = m.group(2);
	        String url = m.group(3);
	        String title = m.group(6);
	        int linkTextLength = linkText.length();
	        
	        if (Constants.LOGGING) Log.d(TAG, "pos="+(start + anchorStart) + " linkText="+linkText + " url="+url + " title="+ title);
	        
	        // protect emphasis (* and _) within urls
//	        url = url.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
//	        url = url.replaceAll("_", CHAR_PROTECTOR.encode("_"));
	        
	        if (!isOverlapping(start + anchorStart, start + anchorStart + linkTextLength, startToEndOffsetMap)) {
	        	saveStartAndEnd(start + anchorStart, start + anchorStart + linkTextLength, startToEndOffsetMap);
	        	urls.add(new MarkdownURL(start + anchorStart, Util.absolutePathToURL(url), linkText));
	        }
	        	
//	        StringBuffer result = new StringBuffer();
	        // TODO: Show title (if any) alongside url in popup menu
//	        if (title != null) {
//	            // protect emphasis (* and _) within urls
//	            title = title.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
//	            title = title.replaceAll("_", CHAR_PROTECTOR.encode("_"));
//	            title.replaceAll("\"", "&quot;");
//	            result.append(" title=\"");
//	            result.append(title);
//	            result.append("\"");
//	        }
	        
        	txt = new StringBuilder(txt.substring(0, start + anchorStart))
        		.append(linkText)
        		.append(txt.substring(start + anchorEnd, txt.length()))
        		.toString();
        	// Skip past what we just replaced
        	am = inlineLinkAutomaton.newMatcher(txt, start + anchorStart + linkTextLength, txt.length());
        	start += anchorStart + linkTextLength;
        }
        return txt;
    }
    
    /**
     * @param txt input text
     * @param urls Out URLs from autolinks
     * @return txt, unchanged
     */
    private String doAutoLinkURLs(String txt, ArrayList<MarkdownURL> urls, TreeMap<Integer, Integer> startToEndOffsetMap) {
        // Colorize URLs
        AutomatonMatcher am = autoLinkUrlAutomaton.newMatcher(txt);
        while (am.find()) {
        	String linkText = am.group();
        	String url = Util.absolutePathToURL(am.group());
	        if (Constants.LOGGING) Log.d(TAG, "pos="+am.start() + " linkText="+linkText + " url="+url);
	        if (!isOverlapping(am.start(), am.start() + linkText.length(), startToEndOffsetMap)) {
	        	saveStartAndEnd(am.start(), am.start() + linkText.length(), startToEndOffsetMap);
	        	urls.add(new MarkdownURL(am.start(), url, null));
	        }
        }
        // Don't autolink emails for now. Neither does reddit.com
//        m = autoLinkEmail.matcher(ssb);
//        int start = 0;
//        while (m.find(start)) {
//        	String address = m.group(1);
//            TextEditor ed = new TextEditor(address);
//            unEscapeSpecialChars(ed);
//            String addr = encodeEmail(ed.toString());
//            String url = encodeEmail("mailto:" + ed.toString());
//        	
//        	urls.add(url);
//        	ssb.replace(m.start(), m.end(), addr);
//        	ForegroundColorSpan fcs = new ForegroundColorSpan(Constants.MARKDOWN_LINK_COLOR);
//        	ssb.setSpan(fcs, m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//        	// Skip what we just replaced
//        	m = autoLinkEmail.matcher(ssb);
//        	start = m.start() + addr.length();
//        }
        return txt;
    }
    
    /**
     * @param txt input text
     * @param urls Out URLs from subreddit references
     * @return txt, unchanged
     */
    private String doAutoLinkSubredditURLs(String txt, ArrayList<MarkdownURL> urls, TreeMap<Integer, Integer> startToEndOffsetMap) {
        AutomatonMatcher am = subredditAutomaton.newMatcher(txt);
        while (am.find()) {
        	String subreddit = am.group();
        	if (Constants.LOGGING) Log.d(TAG, "pos="+am.start() + " subreddit="+subreddit);
	        if (!isOverlapping(am.start(), am.start() + subreddit.length(), startToEndOffsetMap)) {
	        	saveStartAndEnd(am.start(), am.start() + subreddit.length(), startToEndOffsetMap);
	        	urls.add(new MarkdownURL(am.start(), Util.absolutePathToURL(subreddit), subreddit));
	        }
        }
        return txt;
    }
    
	
	/** Adapted from MarkdownJ. Convert links, return URL */
    private SpannableStringBuilder doAnchors(SpannableStringBuilder ssb, ArrayList<MarkdownURL> urls) {
    	// Inline-style links: [link text](url "optional title")
        AutomatonMatcher am = inlineLinkAutomaton.newMatcher(ssb);
        // The offset into the entire original string 
        int start = 0;
        while (am.find()) {
        	// The offsets from start (offset into orig. string = start + anchorStart)
        	int anchorStart = am.start();
        	int anchorEnd = am.end();
        	Matcher m = inlineLink.matcher(am.group());
        	if (!m.find())
        		continue;
        	String linkText = m.group(2);
	        String url = m.group(3);
	        String title = m.group(6);
	        int linkTextLength = linkText.length();
	        
	        if (Constants.LOGGING) Log.d(TAG, "pos="+am.start() + " linkText="+linkText + " url="+url + " title="+ title);
	        
	        // protect emphasis (* and _) within urls
//	        url = url.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
//	        url = url.replaceAll("_", CHAR_PROTECTOR.encode("_"));
	        urls.add(new MarkdownURL(start + anchorStart, url, linkText));
//	        StringBuffer result = new StringBuffer();
	        // TODO: Show title (if any) alongside url in popup menu
//	        if (title != null) {
//	            // protect emphasis (* and _) within urls
//	            title = title.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
//	            title = title.replaceAll("_", CHAR_PROTECTOR.encode("_"));
//	            title.replaceAll("\"", "&quot;");
//	            result.append(" title=\"");
//	            result.append(title);
//	            result.append("\"");
//	        }
	        
	        // Replace whole anchor thing with just linkText, colored different color
	        SpannableString ss = new SpannableString(linkText);
	        ForegroundColorSpan fcs = new ForegroundColorSpan(Constants.MARKDOWN_LINK_COLOR);
        	ss.setSpan(fcs, 0, linkTextLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        	ssb = ssb.replace(start + anchorStart, start + anchorEnd, ss);
        	// Skip past what we just replaced
        	am = inlineLinkAutomaton.newMatcher(ssb, start + anchorStart + linkTextLength, ssb.length());
        	start += anchorStart + linkTextLength;
        }
        return ssb;
    }

    private SpannableStringBuilder doAutoLinks(SpannableStringBuilder ssb, ArrayList<MarkdownURL> urls) {
        // Colorize URLs
        AutomatonMatcher am = autoLinkUrlAutomaton.newMatcher(ssb);
        while (am.find()) {
        	ForegroundColorSpan fcs = new ForegroundColorSpan(Constants.MARKDOWN_LINK_COLOR);
        	ssb.setSpan(fcs, am.start(), am.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	        urls.add(new MarkdownURL(am.start(), am.group(), null));
        }
        // Don't autolink emails for now. Neither does reddit.com
//        m = autoLinkEmail.matcher(ssb);
//        int start = 0;
//        while (m.find(start)) {
//        	String address = m.group(1);
//            TextEditor ed = new TextEditor(address);
//            unEscapeSpecialChars(ed);
//            String addr = encodeEmail(ed.toString());
//            String url = encodeEmail("mailto:" + ed.toString());
//        	
//        	urls.add(url);
//        	ssb.replace(m.start(), m.end(), addr);
//        	ForegroundColorSpan fcs = new ForegroundColorSpan(Constants.MARKDOWN_LINK_COLOR);
//        	ssb.setSpan(fcs, m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//        	// Skip what we just replaced
//        	m = autoLinkEmail.matcher(ssb);
//        	start = m.start() + addr.length();
//        }
        return ssb;
    }
    
//    private String encodeEmail(String s) {
//        StringBuffer sb = new StringBuffer();
//        char[] email = s.toCharArray();
//        for (char ch : email) {
//            double r = rnd.nextDouble();
//            if (r < 0.45) { // Decimal
//                sb.append("&#");
//                sb.append((int) ch);
//                sb.append(';');
//            } else if (r < 0.9) { // Hex
//                sb.append("&#x");
//                sb.append(Integer.toString((int) ch, 16));
//                sb.append(';');
//            } else {
//                sb.append(ch);
//            }
//        }
//        return sb.toString();
//    }
//    
//    private void unEscapeSpecialChars(TextEditor ed) {
//        for (String hash : CHAR_PROTECTOR.getAllEncodedTokens()) {
//            String plaintext = CHAR_PROTECTOR.decode(hash);
//            ed.replaceAllLiteral(hash, plaintext);
//        }
//    }
}
