/*******************************************************************************
 * Copyright (c) 2003, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.set.browser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.browser.AuthenticationListener;
import org.eclipse.swt.browser.CloseWindowListener;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.browser.VisibilityWindowListener;
import org.eclipse.swt.widgets.Composite;

public abstract class WebBrowser {
	public class EvaluateFunction extends BrowserFunction {
		public EvaluateFunction(final Browser browser, final String name) {
			super(browser, name, true, new String[0], false);
		}

		@Override
		public Object function(final Object[] arguments) {
			if (arguments[0] instanceof String) {
				final String string = (String) arguments[0];
				if (string.startsWith(ERROR_ID)) {
					final String errorString = ExtractError(string);
					if (errorString.length() > 0) {
						evaluateResult = new SWTException(
								SWT.ERROR_FAILED_EVALUATE, errorString);
					} else {
						evaluateResult = new SWTException(
								SWT.ERROR_FAILED_EVALUATE);
					}
					return null;
				}
			}
			evaluateResult = arguments[0];
			return null;
		}
	}

	public static String CookieName;
	public static boolean CookieResult;
	public static String CookieUrl;
	public static String CookieValue;
	public static Runnable NativeClearSessions;
	public static Runnable NativeGetCookie;
	public static Runnable NativeSetCookie;
	static final String ERROR_ID = "org.eclipse.swt.browser.error"; // $NON-NLS-1$
	static final String EXECUTE_ID = "SWTExecuteTemporaryFunction"; // $NON-NLS-1$
	/* Key Mappings */
	static final int[][] KeyTable = {
			/* Keyboard and Mouse Masks */
			{ 18, SWT.ALT }, { 16, SWT.SHIFT }, { 17, SWT.CONTROL },
			{ 224, SWT.COMMAND },

			/* Literal Keys */
			{ 65, 'a' }, { 66, 'b' }, { 67, 'c' }, { 68, 'd' }, { 69, 'e' },
			{ 70, 'f' }, { 71, 'g' }, { 72, 'h' }, { 73, 'i' }, { 74, 'j' },
			{ 75, 'k' }, { 76, 'l' }, { 77, 'm' }, { 78, 'n' }, { 79, 'o' },
			{ 80, 'p' }, { 81, 'q' }, { 82, 'r' }, { 83, 's' }, { 84, 't' },
			{ 85, 'u' }, { 86, 'v' }, { 87, 'w' }, { 88, 'x' }, { 89, 'y' },
			{ 90, 'z' }, { 48, '0' }, { 49, '1' }, { 50, '2' }, { 51, '3' },
			{ 52, '4' }, { 53, '5' }, { 54, '6' }, { 55, '7' }, { 56, '8' },
			{ 57, '9' }, { 32, ' ' }, { 59, ';' }, { 61, '=' }, { 188, ',' },
			{ 190, '.' }, { 191, '/' }, { 219, '[' }, { 221, ']' },
			{ 222, '\'' }, { 192, '`' }, { 220, '\\' }, { 108, '|' },
			{ 226, '<' },

			/* Non-Numeric Keypad Keys */
			{ 37, SWT.ARROW_LEFT }, { 39, SWT.ARROW_RIGHT },
			{ 38, SWT.ARROW_UP }, { 40, SWT.ARROW_DOWN }, { 45, SWT.INSERT },
			{ 36, SWT.HOME }, { 35, SWT.END }, { 46, SWT.DEL },
			{ 33, SWT.PAGE_UP }, { 34, SWT.PAGE_DOWN },

			/* Virtual and Ascii Keys */
			{ 8, SWT.BS }, { 13, SWT.CR }, { 9, SWT.TAB }, { 27, SWT.ESC },
			{ 12, SWT.DEL },

			/* Functions Keys */
			{ 112, SWT.F1 }, { 113, SWT.F2 }, { 114, SWT.F3 }, { 115, SWT.F4 },
			{ 116, SWT.F5 }, { 117, SWT.F6 }, { 118, SWT.F7 }, { 119, SWT.F8 },
			{ 120, SWT.F9 }, { 121, SWT.F10 }, { 122, SWT.F11 },
			{ 123, SWT.F12 }, { 124, SWT.F13 }, { 125, SWT.F14 },
			{ 126, SWT.F15 }, { 127, 0 }, { 128, 0 }, { 129, 0 }, { 130, 0 },
			{ 131, 0 }, { 132, 0 }, { 133, 0 }, { 134, 0 }, { 135, 0 },

			/* Numeric Keypad Keys */
			{ 96, SWT.KEYPAD_0 }, { 97, SWT.KEYPAD_1 }, { 98, SWT.KEYPAD_2 },
			{ 99, SWT.KEYPAD_3 }, { 100, SWT.KEYPAD_4 }, { 101, SWT.KEYPAD_5 },
			{ 102, SWT.KEYPAD_6 }, { 103, SWT.KEYPAD_7 }, { 104, SWT.KEYPAD_8 },
			{ 105, SWT.KEYPAD_9 }, { 14, SWT.KEYPAD_CR },
			{ 107, SWT.KEYPAD_ADD }, { 109, SWT.KEYPAD_SUBTRACT },
			{ 106, SWT.KEYPAD_MULTIPLY }, { 111, SWT.KEYPAD_DIVIDE },
			{ 110, SWT.KEYPAD_DECIMAL },

			/* Other keys */
			{ 20, SWT.CAPS_LOCK }, { 144, SWT.NUM_LOCK },
			{ 145, SWT.SCROLL_LOCK }, { 44, SWT.PRINT_SCREEN }, { 6, SWT.HELP },
			{ 19, SWT.PAUSE }, { 3, SWT.BREAK },

			/* WebKit-specific */
			{ 186, ';' }, { 187, '=' }, { 189, '-' }, };
	static List<String[]> NativePendingCookies = new ArrayList<>();

	public static void clearSessions() {
		if (NativeClearSessions != null) {
			NativeClearSessions.run();
		}
	}

	public static String GetCookie(final String name, final String url) {
		CookieName = name;
		CookieUrl = url;
		CookieValue = null;
		if (NativeGetCookie != null) {
			NativeGetCookie.run();
		}
		final String result = CookieValue;
		CookieName = CookieValue = CookieUrl = null;
		return result;
	}

	public static boolean SetCookie(final String value, final String url,
			final boolean addToPending) {
		CookieValue = value;
		CookieUrl = url;
		CookieResult = false;
		if (NativeSetCookie != null) {
			NativeSetCookie.run();
		} else {
			if (addToPending && NativePendingCookies != null) {
				NativePendingCookies.add(new String[] { value, url });
			}
		}
		CookieValue = CookieUrl = null;
		return CookieResult;
	}

	static String CreateErrorString(final String error) {
		return ERROR_ID + error;
	}

	static String ExtractError(final String error) {
		return error.substring(ERROR_ID.length());
	}

	static String getDeleteFunctionString(final String functionName) {
		return "delete window." + functionName; //$NON-NLS-1$
	}

	/**
	 * Designed to be overriden.
	 * 
	 * @return javaScrit code that defines the 'callJava' syntax for javascript.
	 */
	static String getJavaCallDeclaration() {
		return "if (!window.callJava) {\n"
				+ "		window.callJava = function callJava(index, token, args) {\n"
				+ "			return external.callJava(index,token,args);\n"
				+ "		}\n" + "};\n";
	}

	static void SetPendingCookies(final List<String[]> pendingCookies) {
		for (final String[] current : pendingCookies) {
			SetCookie(current[0], current[1], false);
		}
	}

	protected AuthenticationListener[] authenticationListeners = new AuthenticationListener[0];
	protected Browser browser;
	protected CloseWindowListener[] closeWindowListeners = new CloseWindowListener[0];

	protected Object evaluateResult;
	protected Map<Integer, BrowserFunction> functions = new HashMap<>();

	protected boolean jsEnabled = true;

	protected boolean jsEnabledOnNextPage = true;

	protected LocationListener[] locationListeners = new LocationListener[0];

	protected int nextFunctionIndex = 1;

	protected OpenWindowListener[] openWindowListeners = new OpenWindowListener[0];

	protected ProgressListener[] progressListeners = new ProgressListener[0];

	protected StatusTextListener[] statusTextListeners = new StatusTextListener[0];

	protected TitleListener[] titleListeners = new TitleListener[0];

	protected VisibilityWindowListener[] visibilityWindowListeners = new VisibilityWindowListener[0];

	public void addAuthenticationListener(
			final AuthenticationListener listener) {
		final AuthenticationListener[] newAuthenticationListeners = new AuthenticationListener[authenticationListeners.length
				+ 1];
		System.arraycopy(authenticationListeners, 0, newAuthenticationListeners,
				0, authenticationListeners.length);
		authenticationListeners = newAuthenticationListeners;
		authenticationListeners[authenticationListeners.length - 1] = listener;
	}

	public void addCloseWindowListener(final CloseWindowListener listener) {
		final CloseWindowListener[] newCloseWindowListeners = new CloseWindowListener[closeWindowListeners.length
				+ 1];
		System.arraycopy(closeWindowListeners, 0, newCloseWindowListeners, 0,
				closeWindowListeners.length);
		closeWindowListeners = newCloseWindowListeners;
		closeWindowListeners[closeWindowListeners.length - 1] = listener;
	}

	public void addLocationListener(final LocationListener listener) {
		final LocationListener[] newLocationListeners = new LocationListener[locationListeners.length
				+ 1];
		System.arraycopy(locationListeners, 0, newLocationListeners, 0,
				locationListeners.length);
		locationListeners = newLocationListeners;
		locationListeners[locationListeners.length - 1] = listener;
	}

	public void addOpenWindowListener(final OpenWindowListener listener) {
		final OpenWindowListener[] newOpenWindowListeners = new OpenWindowListener[openWindowListeners.length
				+ 1];
		System.arraycopy(openWindowListeners, 0, newOpenWindowListeners, 0,
				openWindowListeners.length);
		openWindowListeners = newOpenWindowListeners;
		openWindowListeners[openWindowListeners.length - 1] = listener;
	}

	public void addProgressListener(final ProgressListener listener) {
		final ProgressListener[] newProgressListeners = new ProgressListener[progressListeners.length
				+ 1];
		System.arraycopy(progressListeners, 0, newProgressListeners, 0,
				progressListeners.length);
		progressListeners = newProgressListeners;
		progressListeners[progressListeners.length - 1] = listener;
	}

	public void addStatusTextListener(final StatusTextListener listener) {
		final StatusTextListener[] newStatusTextListeners = new StatusTextListener[statusTextListeners.length
				+ 1];
		System.arraycopy(statusTextListeners, 0, newStatusTextListeners, 0,
				statusTextListeners.length);
		statusTextListeners = newStatusTextListeners;
		statusTextListeners[statusTextListeners.length - 1] = listener;
	}

	public void addTitleListener(final TitleListener listener) {
		final TitleListener[] newTitleListeners = new TitleListener[titleListeners.length
				+ 1];
		System.arraycopy(titleListeners, 0, newTitleListeners, 0,
				titleListeners.length);
		titleListeners = newTitleListeners;
		titleListeners[titleListeners.length - 1] = listener;
	}

	public void addVisibilityWindowListener(
			final VisibilityWindowListener listener) {
		final VisibilityWindowListener[] newVisibilityWindowListeners = new VisibilityWindowListener[visibilityWindowListeners.length
				+ 1];
		System.arraycopy(visibilityWindowListeners, 0,
				newVisibilityWindowListeners, 0,
				visibilityWindowListeners.length);
		visibilityWindowListeners = newVisibilityWindowListeners;
		visibilityWindowListeners[visibilityWindowListeners.length
				- 1] = listener;
	}

	public abstract boolean back();

	public boolean close() {
		return true;
	}

	public abstract void create(Composite parent, int style);

	public void createFunction(final BrowserFunction function) {
		/*
		 * If an existing function with the same name is found then remove it so
		 * that it is not recreated on subsequent pages (the new function
		 * overwrites the old one).
		 */
		for (final BrowserFunction current : functions.values()) {
			if (current.name.equals(function.name)) {
				deregisterFunction(current);
				break;
			}
		}

		function.index = getNextFunctionIndex();
		registerFunction(function);

		final StringBuilder functionBuffer = new StringBuilder(function.name);
		functionBuffer.append(" = function "); //$NON-NLS-1$
		functionBuffer.append(function.name);
		functionBuffer.append("() {var result = callJava("); //$NON-NLS-1$
		functionBuffer.append(function.index);
		functionBuffer.append(",'"); //$NON-NLS-1$
		functionBuffer.append(function.token);
		functionBuffer.append(
				"',Array.prototype.slice.call(arguments)); if (typeof result == 'string' && result.indexOf('"); //$NON-NLS-1$
		functionBuffer.append(ERROR_ID);
		functionBuffer
				.append("') == 0) {var error = new Error(result.substring("); //$NON-NLS-1$
		functionBuffer.append(ERROR_ID.length());
		functionBuffer.append(")); throw error;} return result;};"); //$NON-NLS-1$

		final String javaCallDeclaration = getJavaCallDeclaration();

		final StringBuilder buffer = new StringBuilder();
		buffer.append(javaCallDeclaration);
		if (function.top) {
			buffer.append(functionBuffer.toString());
		}

		buffer.append("var frameIds = null;"); //$NON-NLS-1$
		if (function.frameNames != null) {
			buffer.append("frameIds = {"); //$NON-NLS-1$
			for (final String frameName : function.frameNames) {
				buffer.append('\'');
				buffer.append(frameName);
				buffer.append("':1,"); //$NON-NLS-1$
			}
			if (function.frameNames.length > 0) {
				buffer.deleteCharAt(buffer.length() - 1);
			}
			buffer.append("};"); //$NON-NLS-1$
		}

		buffer.append(
				"for (var i = 0; i < frames.length; i++) {try {if (!frameIds || (frames[i].name && frameIds[frames[i].name])) {"); //$NON-NLS-1$
		buffer.append(
				"if (!frames[i].callJava) {frames[i].callJava = window.callJava;} frames[i]."); //$NON-NLS-1$
		buffer.append(functionBuffer.toString());
		buffer.append("}} catch(e) {}};"); //$NON-NLS-1$

		function.functionString = buffer.toString();
		nonBlockingExecute(function.functionString);
	}

	public void destroyFunction(final BrowserFunction function) {
		final String deleteString = getDeleteFunctionString(function.name);
		final StringBuilder buffer = new StringBuilder(
				"for (var i = 0; i < frames.length; i++) {try {frames[i].eval(\""); //$NON-NLS-1$
		buffer.append(deleteString);
		buffer.append("\");} catch (e) {}}"); //$NON-NLS-1$
		nonBlockingExecute(buffer.toString());
		nonBlockingExecute(deleteString);
		deregisterFunction(function);
	}

	public Object evaluate(final String script) throws SWTException {
		// Gtk Developer note:
		// Webkit1 uses this mechanism.
		// Webkit2 uses a different mechanism. See WebKit:evaluate();
		final BrowserFunction function = new EvaluateFunction(browser, ""); // $NON-NLS-1$
		final int index = getNextFunctionIndex();
		function.index = index;
		function.isEvaluate = true; // Note, Webkit2 doesn't use 'isEvaluate'
									// machinery because it doesn't use a
									// function for evaluation.
		registerFunction(function);
		final String functionName = EXECUTE_ID + index;

		StringBuilder buffer = new StringBuilder("window."); // $NON-NLS-1$
		buffer.append(functionName);
		buffer.append(" = function "); // $NON-NLS-1$
		buffer.append(functionName);
		buffer.append("() {\n"); // $NON-NLS-1$
		buffer.append(script);
		buffer.append("\n};"); // $NON-NLS-1$
		nonBlockingExecute(buffer.toString());

		buffer = new StringBuilder("if (window."); // $NON-NLS-1$
		buffer.append(functionName);
		buffer.append(" == undefined) {window.external.callJava("); // $NON-NLS-1$
		buffer.append(index);
		buffer.append(",'"); //$NON-NLS-1$
		buffer.append(function.token);
		buffer.append("', ['"); // $NON-NLS-1$
		buffer.append(ERROR_ID);
		buffer.append("']);} else {try {var result = "); // $NON-NLS-1$
		buffer.append(functionName);
		buffer.append("(); window.external.callJava("); // $NON-NLS-1$
		buffer.append(index);
		buffer.append(",'"); //$NON-NLS-1$
		buffer.append(function.token);
		buffer.append("', [result]);} catch (e) {window.external.callJava("); // $NON-NLS-1$
		buffer.append(index);
		buffer.append(",'"); //$NON-NLS-1$
		buffer.append(function.token);
		buffer.append("', ['"); // $NON-NLS-1$
		buffer.append(ERROR_ID);
		buffer.append("' + e.message]);}}"); // $NON-NLS-1$
		nonBlockingExecute(buffer.toString());
		nonBlockingExecute(getDeleteFunctionString(functionName));
		deregisterFunction(function);

		final Object result = evaluateResult;
		evaluateResult = null;
		if (result instanceof SWTException) {
			throw (SWTException) result;
		}
		return result;
	}

	public Object evaluate(final String script, final boolean trusted)
			throws SWTException {
		return evaluate(script);
	}

	public abstract boolean execute(String script);

	public abstract boolean forward();

	public abstract String getBrowserType();

	public abstract String getText();

	public abstract String getUrl();

	public Object getWebBrowser() {
		return null;
	}

	public abstract boolean isBackEnabled();

	public boolean isFocusControl() {
		return false;
	}

	public abstract boolean isForwardEnabled();

	public abstract void refresh();

	public void removeAuthenticationListener(
			final AuthenticationListener listener) {
		if (authenticationListeners.length == 0) {
			return;
		}
		int index = -1;
		for (int i = 0; i < authenticationListeners.length; i++) {
			if (listener == authenticationListeners[i]) {
				index = i;
				break;
			}
		}
		if (index == -1) {
			return;
		}
		if (authenticationListeners.length == 1) {
			authenticationListeners = new AuthenticationListener[0];
			return;
		}
		final AuthenticationListener[] newAuthenticationListeners = new AuthenticationListener[authenticationListeners.length
				- 1];
		System.arraycopy(authenticationListeners, 0, newAuthenticationListeners,
				0, index);
		System.arraycopy(authenticationListeners, index + 1,
				newAuthenticationListeners, index,
				authenticationListeners.length - index - 1);
		authenticationListeners = newAuthenticationListeners;
	}

	public void removeCloseWindowListener(final CloseWindowListener listener) {
		if (closeWindowListeners.length == 0) {
			return;
		}
		int index = -1;
		for (int i = 0; i < closeWindowListeners.length; i++) {
			if (listener == closeWindowListeners[i]) {
				index = i;
				break;
			}
		}
		if (index == -1) {
			return;
		}
		if (closeWindowListeners.length == 1) {
			closeWindowListeners = new CloseWindowListener[0];
			return;
		}
		final CloseWindowListener[] newCloseWindowListeners = new CloseWindowListener[closeWindowListeners.length
				- 1];
		System.arraycopy(closeWindowListeners, 0, newCloseWindowListeners, 0,
				index);
		System.arraycopy(closeWindowListeners, index + 1,
				newCloseWindowListeners, index,
				closeWindowListeners.length - index - 1);
		closeWindowListeners = newCloseWindowListeners;
	}

	public void removeLocationListener(final LocationListener listener) {
		if (locationListeners.length == 0) {
			return;
		}
		int index = -1;
		for (int i = 0; i < locationListeners.length; i++) {
			if (listener == locationListeners[i]) {
				index = i;
				break;
			}
		}
		if (index == -1) {
			return;
		}
		if (locationListeners.length == 1) {
			locationListeners = new LocationListener[0];
			return;
		}
		final LocationListener[] newLocationListeners = new LocationListener[locationListeners.length
				- 1];
		System.arraycopy(locationListeners, 0, newLocationListeners, 0, index);
		System.arraycopy(locationListeners, index + 1, newLocationListeners,
				index, locationListeners.length - index - 1);
		locationListeners = newLocationListeners;
	}

	public void removeOpenWindowListener(final OpenWindowListener listener) {
		if (openWindowListeners.length == 0) {
			return;
		}
		int index = -1;
		for (int i = 0; i < openWindowListeners.length; i++) {
			if (listener == openWindowListeners[i]) {
				index = i;
				break;
			}
		}
		if (index == -1) {
			return;
		}
		if (openWindowListeners.length == 1) {
			openWindowListeners = new OpenWindowListener[0];
			return;
		}
		final OpenWindowListener[] newOpenWindowListeners = new OpenWindowListener[openWindowListeners.length
				- 1];
		System.arraycopy(openWindowListeners, 0, newOpenWindowListeners, 0,
				index);
		System.arraycopy(openWindowListeners, index + 1, newOpenWindowListeners,
				index, openWindowListeners.length - index - 1);
		openWindowListeners = newOpenWindowListeners;
	}

	public void removeProgressListener(final ProgressListener listener) {
		if (progressListeners.length == 0) {
			return;
		}
		int index = -1;
		for (int i = 0; i < progressListeners.length; i++) {
			if (listener == progressListeners[i]) {
				index = i;
				break;
			}
		}
		if (index == -1) {
			return;
		}
		if (progressListeners.length == 1) {
			progressListeners = new ProgressListener[0];
			return;
		}
		final ProgressListener[] newProgressListeners = new ProgressListener[progressListeners.length
				- 1];
		System.arraycopy(progressListeners, 0, newProgressListeners, 0, index);
		System.arraycopy(progressListeners, index + 1, newProgressListeners,
				index, progressListeners.length - index - 1);
		progressListeners = newProgressListeners;
	}

	public void removeStatusTextListener(final StatusTextListener listener) {
		if (statusTextListeners.length == 0) {
			return;
		}
		int index = -1;
		for (int i = 0; i < statusTextListeners.length; i++) {
			if (listener == statusTextListeners[i]) {
				index = i;
				break;
			}
		}
		if (index == -1) {
			return;
		}
		if (statusTextListeners.length == 1) {
			statusTextListeners = new StatusTextListener[0];
			return;
		}
		final StatusTextListener[] newStatusTextListeners = new StatusTextListener[statusTextListeners.length
				- 1];
		System.arraycopy(statusTextListeners, 0, newStatusTextListeners, 0,
				index);
		System.arraycopy(statusTextListeners, index + 1, newStatusTextListeners,
				index, statusTextListeners.length - index - 1);
		statusTextListeners = newStatusTextListeners;
	}

	public void removeTitleListener(final TitleListener listener) {
		if (titleListeners.length == 0) {
			return;
		}
		int index = -1;
		for (int i = 0; i < titleListeners.length; i++) {
			if (listener == titleListeners[i]) {
				index = i;
				break;
			}
		}
		if (index == -1) {
			return;
		}
		if (titleListeners.length == 1) {
			titleListeners = new TitleListener[0];
			return;
		}
		final TitleListener[] newTitleListeners = new TitleListener[titleListeners.length
				- 1];
		System.arraycopy(titleListeners, 0, newTitleListeners, 0, index);
		System.arraycopy(titleListeners, index + 1, newTitleListeners, index,
				titleListeners.length - index - 1);
		titleListeners = newTitleListeners;
	}

	public void removeVisibilityWindowListener(
			final VisibilityWindowListener listener) {
		if (visibilityWindowListeners.length == 0) {
			return;
		}
		int index = -1;
		for (int i = 0; i < visibilityWindowListeners.length; i++) {
			if (listener == visibilityWindowListeners[i]) {
				index = i;
				break;
			}
		}
		if (index == -1) {
			return;
		}
		if (visibilityWindowListeners.length == 1) {
			visibilityWindowListeners = new VisibilityWindowListener[0];
			return;
		}
		final VisibilityWindowListener[] newVisibilityWindowListeners = new VisibilityWindowListener[visibilityWindowListeners.length
				- 1];
		System.arraycopy(visibilityWindowListeners, 0,
				newVisibilityWindowListeners, 0, index);
		System.arraycopy(visibilityWindowListeners, index + 1,
				newVisibilityWindowListeners, index,
				visibilityWindowListeners.length - index - 1);
		visibilityWindowListeners = newVisibilityWindowListeners;
	}

	public void setBrowser(final Browser browser) {
		this.browser = browser;
	}

	public abstract boolean setText(String html, boolean trusted);

	public abstract boolean setUrl(String url, String postData,
			String[] headers);

	public abstract void stop();

	protected void deregisterFunction(final BrowserFunction function) {
		functions.remove(function.index);
	}

	protected int getNextFunctionIndex() {
		return nextFunctionIndex++;
	}

	protected void registerFunction(final BrowserFunction function) {
		functions.put(function.index, function);
	}

	// Designed to be overriden by platform implementations, used for
	// optimization and avoiding deadlocks.
	// Webkit2 is async, we often don't need to bother waiting for a return type
	// if we never use it.
	void nonBlockingExecute(final String script) {
		execute(script);
	}
}
