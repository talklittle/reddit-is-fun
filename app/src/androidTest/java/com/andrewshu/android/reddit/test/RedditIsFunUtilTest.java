package com.andrewshu.android.reddit.test;

import java.lang.reflect.Array;
import java.util.Arrays;

import com.andrewshu.android.reddit.Constants;
import com.andrewshu.android.reddit.Util;
import com.andrewshu.android.reddit.R;

import junit.framework.TestCase;

public class RedditIsFunUtilTest extends TestCase {

	public void testIsLightTheme() 
	{
		
		boolean result = Util.isLightTheme(R.style.Reddit_Light_Medium) &&
		Util.isLightTheme(R.style.Reddit_Light_Large) &&
		Util.isLightTheme(R.style.Reddit_Light_Larger) &&
		Util.isLightTheme(R.style.Reddit_Light_Huge); 
		
		assertTrue(result);
		
	}

	public void testIsDarkTheme() 
	{
		
		boolean result = Util.isDarkTheme(R.style.Reddit_Dark_Medium) &&
		Util.isDarkTheme(R.style.Reddit_Dark_Large) &&
		Util.isDarkTheme(R.style.Reddit_Dark_Larger) &&
		Util.isDarkTheme(R.style.Reddit_Dark_Huge);

		assertTrue(result);
		
	}

	public void testGetInvertedTheme() 
	{
		
		assertEquals("GetInvertedTheme From Dark to Light Failed", R.style.Reddit_Dark_Huge, Util.getInvertedTheme(R.style.Reddit_Light_Huge));
		
		assertEquals("GetInvertedTheme From Light to Dark Failed", R.style.Reddit_Light_Huge, Util.getInvertedTheme(R.style.Reddit_Dark_Huge));
		
	}

	public void testGetThemeResourceFromPrefs() 
	{

		assertEquals("GetThemeResourceFromPrefs for Dark Theme Failed", R.style.Reddit_Dark_Huge, Util.getThemeResourceFromPrefs(Constants.PREF_THEME_DARK, Constants.PREF_TEXT_SIZE_HUGE));
		
		assertEquals("GetThemeResourceFromPrefs for Light Theme Failed", R.style.Reddit_Light_Huge, Util.getThemeResourceFromPrefs(Constants.PREF_THEME_LIGHT, Constants.PREF_TEXT_SIZE_HUGE));
		
	}

	public void testGetPrefsFromThemeResource() 
	{
		
		boolean blnResultForDarkTheme;
		boolean blnResultForLightTheme;
		
		String [] astrDarkHugePrefs = new String[] { Constants.PREF_THEME_DARK, Constants.PREF_TEXT_SIZE_HUGE };
		String [] astrLightHugePrefs = new String[] { Constants.PREF_THEME_LIGHT, Constants.PREF_TEXT_SIZE_HUGE };
		
		blnResultForDarkTheme = Arrays.equals(astrDarkHugePrefs, Util.getPrefsFromThemeResource(R.style.Reddit_Dark_Huge));
		blnResultForLightTheme = Arrays.equals(astrLightHugePrefs, Util.getPrefsFromThemeResource(R.style.Reddit_Light_Huge));
		
		assertTrue(blnResultForDarkTheme && blnResultForLightTheme);
		
	}

	public void testGetTextAppearanceResource() 
	{
		
		assertEquals("GetTextAppearanceResource for Dark Theme Small Text Failed", R.style.TextAppearance_Huge_Small, Util.getTextAppearanceResource(R.style.Reddit_Dark_Huge, android.R.style.TextAppearance_Small));
		
		assertEquals("GetTextAppearanceResource for Dark Theme Medium Text Failed", R.style.TextAppearance_Huge_Medium, Util.getTextAppearanceResource(R.style.Reddit_Dark_Huge, android.R.style.TextAppearance_Medium));
		
		assertEquals("GetTextAppearanceResource for Dark Theme Large Text Failed", R.style.TextAppearance_Huge_Large, Util.getTextAppearanceResource(R.style.Reddit_Dark_Huge, android.R.style.TextAppearance_Large));
		
		assertEquals("GetTextAppearanceResource for Light Theme Small Text Failed", R.style.TextAppearance_Huge_Small, Util.getTextAppearanceResource(R.style.Reddit_Light_Huge, android.R.style.TextAppearance_Small));
		
		assertEquals("GetTextAppearanceResource for Light Theme Medium Text Failed", R.style.TextAppearance_Huge_Medium, Util.getTextAppearanceResource(R.style.Reddit_Light_Huge, android.R.style.TextAppearance_Medium));
		
		assertEquals("GetTextAppearanceResource for Light Theme Large Text Failed", R.style.TextAppearance_Huge_Large, Util.getTextAppearanceResource(R.style.Reddit_Light_Huge, android.R.style.TextAppearance_Large));
		
		
	}

}
