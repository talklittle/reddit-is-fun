/*
 * Copyright 2010 Andrew Shu
 *
 * This file is part of "diode".
 *
 * "diode" is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * "diode" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "diode".  If not, see <http://www.gnu.org/licenses/>.
 */
package in.shick.diode.captcha;

public class CaptchaException extends Exception {
	static final long serialVersionUID = 40;
	
	public CaptchaException(String message) {
		super(message);
	}
}
