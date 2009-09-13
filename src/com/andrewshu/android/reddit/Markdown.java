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
package com.andrewshu.android.reddit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import com.petebevin.markdown.TextEditor;

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
    private static final MarkdownCharacterProtector CHAR_PROTECTOR = new MarkdownCharacterProtector();
    
    static final RunAutomaton inlineLinkAutomaton = new RunAutomaton(new RegExp("(" + // Whole match = $1
            "\\[([^\\]]*)\\]" + // Link text = $2
            "\\(" +
            "[ \t]*" +
            "<?([^>]*)>?" + // href = $3
            "[ \t]*" +
            "(" +
            "('[^']*'|\\\"[^\"]*\\\")" + // Quoted title = $5
            ")?" +
            "\\)" +
            ")",
            RegExp.NONE).toAutomaton());
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
	
    /**
	* Perform the conversion from Markdown to HTML.
	*
	* @param txt - input in markdown format
	* @return HTML block corresponding to txt passed in.
	*/
    public SpannableStringBuilder markdown(String txt, SpannableStringBuilder ssb, ArrayList<MarkdownURL> urls) {
    	if (txt == null) {
            txt = "";
        }
        TextEditor text = new TextEditor(txt);
 
        // Standardize line endings:
        text.replaceAll("\\r\\n", "\n"); // DOS to Unix
        text.replaceAll("\\r", "\n"); // Mac to Unix
        text.replaceAll("^[ \\t]+$", "");
 
//        // Make sure $text ends with a couple of newlines:
//        text.append("\n\n");
 
//        text.detabify();
        text.deleteAll("^[ ]+$");
        
        String updatedTxt = text.toString();
        ssb.append(updatedTxt);
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
	
	/** Adapted from MarkdownJ. Convert links, return URL */
    private SpannableStringBuilder doAnchors(SpannableStringBuilder ssb, ArrayList<MarkdownURL> urls) {
    	// Inline-style links: [link text](url "optional title")
        AutomatonMatcher am = inlineLinkAutomaton.newMatcher(ssb);
        int start = 0;
        while (am.find()) {
        	int anchorStart = am.start();
        	int anchorEnd = am.end();
        	Matcher m = inlineLink.matcher(am.group());
        	if (!m.find())
        		continue;
        	String linkText = m.group(2);
	        String url = m.group(3);
	        String title = m.group(6);
	        int linkTextLength = linkText.length();
	        // protect emphasis (* and _) within urls
//	        url = url.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
//	        url = url.replaceAll("_", CHAR_PROTECTOR.encode("_"));
	        urls.add(new MarkdownURL(anchorStart, url));
//	        StringBuffer result = new StringBuffer();
	        // TODO: Show title (if any) alongside url in popup menu
	        if (title != null) {
	            // protect emphasis (* and _) within urls
	            title = title.replaceAll("\\*", CHAR_PROTECTOR.encode("*"));
	            title = title.replaceAll("_", CHAR_PROTECTOR.encode("_"));
	            title.replaceAll("\"", "&quot;");
//	            result.append(" title=\"");
//	            result.append(title);
//	            result.append("\"");
	        }
	        
	        // Replace whole anchor thing with just linkText, colored different color
	        SpannableString ss = new SpannableString(linkText);
	        ForegroundColorSpan fcs = new ForegroundColorSpan(Constants.MARKDOWN_LINK_COLOR);
        	ss.setSpan(fcs, 0, linkTextLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        	ssb = ssb.replace(anchorStart, anchorEnd, ss);
        	// Skip past what we just replaced
        	start = anchorStart + linkText.length();
        	am = inlineLinkAutomaton.newMatcher(ssb, start, ssb.length());
        }
        return ssb;
    }

    private SpannableStringBuilder doAutoLinks(SpannableStringBuilder ssb, ArrayList<MarkdownURL> urls) {
        // Colorize URLs
        AutomatonMatcher am = autoLinkUrlAutomaton.newMatcher(ssb);
        while (am.find()) {
        	ForegroundColorSpan fcs = new ForegroundColorSpan(Constants.MARKDOWN_LINK_COLOR);
        	ssb.setSpan(fcs, am.start(), am.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	        urls.add(new MarkdownURL(am.start(), am.group()));
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
