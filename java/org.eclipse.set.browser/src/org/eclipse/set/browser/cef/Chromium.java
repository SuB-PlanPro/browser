/**
 * Copyright (c) 2022 DB Netz AG and others.
 * Copyright (c) 2020 Equo
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Guillermo Zunino, Equo - initial implementation
 */
package org.eclipse.set.browser.cef;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.set.browser.RequestHandler;
import org.eclipse.set.browser.WebBrowser;
import org.eclipse.set.browser.cef.CEFFactory.ReturnType;
import org.eclipse.set.browser.cef.handlers.ResourceHandler;
import org.eclipse.set.browser.cef.handlers.StringVisitor;
import org.eclipse.set.browser.cef.handlers.browser.ClientHandler;
import org.eclipse.set.browser.cef.handlers.browser.PopupClientHandler;
import org.eclipse.set.browser.lib.ChromiumLib;
import org.eclipse.set.browser.lib.FunctionSt;
import org.eclipse.set.browser.lib.cef_browser_t;
import org.eclipse.set.browser.lib.cef_popup_features_t;
import org.eclipse.set.browser.swt.Browser;
import org.eclipse.set.browser.swt.BrowserFunction;
import org.eclipse.set.browser.swt.OpenWindowListener;
import org.eclipse.set.browser.swt.WindowEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.browser.AuthenticationEvent;
import org.eclipse.swt.browser.AuthenticationListener;
import org.eclipse.swt.browser.CloseWindowListener;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.StatusTextEvent;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.browser.TitleEvent;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.browser.VisibilityWindowListener;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.C;
import org.eclipse.swt.internal.Callback;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/**
 * Chromium WebBrowser implementation
 */
public class Chromium extends WebBrowser {
	/**
	 * Interface for JS Callbacks
	 */
	public static interface EvalReturned {
		/**
		 * @param loop
		 * @param type
		 *            type of the result
		 * @param value
		 *            value of the result
		 */
		void invoke(int loop, int type, long value);
	}

	final class CEFFocusListener implements FocusListener {
		private boolean enabled = true;

		@Override
		public void focusGained(final FocusEvent e) {
			if (!enabled) {
				return;
			}
			browserFocus(true);
		}

		@Override
		public void focusLost(final FocusEvent e) {
			if (!enabled) {
				return;
			}
			enabled = false;
			browserFocus(false);
			enabled = true;
		}
	}

	enum Dispose {
		DoIt, FromBrowser, FromClose, FromDispose, No, Unload, UnloadClosed, WaitIfClosed,
	}

	private static final String DATA_TEXT_URL = "data:text/html;base64,";

	private static int EVAL = 1;

	private static final int LOOP = 75;

	private static final int MAX_PROGRESS = 100;

	private static final String SET_TEXT_URL = "swt.chromium.setText.";

	private static int cefColor(final int a, final int r, final int g,
			final int b) {
		return a << 24 | r << 16 | g << 8 | b << 0;
	}

	private static String getPlainUrl(final String url) {
		if (url != null && url.startsWith(DATA_TEXT_URL)) {
			return url.substring(0, DATA_TEXT_URL.length() - 8);
		}
		if (url != null && url.startsWith("file:/")
				&& url.contains(SET_TEXT_URL)) {
			return "about:blank";
		}
		return url;
	}

	protected static long getHandle(final Composite control) {
		return control.handle;
	}

	static long checkGetAddress(final Callback cb) {
		final long address = cb.getAddress();
		if (address == 0) {
			throw new SWTError(SWT.ERROR_NO_MORE_CALLBACKS);
		}
		return address;
	}

	static void disposeCallback(final Callback cb) {
		if (cb != null) {
			cb.dispose();
		}
	}

	static void doSetUrlPost(final long browser_id, final String url,
			final String postData, final String[] headers) {
		final byte[] bytes = postData != null
				? postData.getBytes(Charset.forName("ASCII"))
				: null;
		final int bytesLength = bytes != null ? bytes.length : 0;
		final int headersLength = headers != null ? headers.length : 0;
		final String joinHeaders = headers == null ? null
				: String.join("::", headers);
		cef_browser_t.cefswt_load_url(browser_id, url, bytes, bytesLength,
				joinHeaders, headersLength);
	}

	static void freeDelayed(final long ptr) {
		Display.getDefault().asyncExec(() -> C.free(ptr));
	}

	@SuppressWarnings("hiding")
	private long browser;

	private int browserId;

	private boolean canGoBack;

	private boolean canGoForward;

	private ClientHandler clientHandler;

	private final CompletableFuture<Boolean> created = new CompletableFuture<>();

	private Dispose disposing = Dispose.No;

	private CompletableFuture<Boolean> enableProgress = new CompletableFuture<>();

	private FocusListener focusListener;

	private boolean hasFocus;

	private String[] headers;

	private long hwnd;

	private boolean ignoreFirstFocus = true;

	private WindowEvent isPopup;

	private PaintListener paintListener;

	private String postData;

	private String text = "";

	private final CompletableFuture<Boolean> textReady = new CompletableFuture<>();

	private StringVisitor textVisitor;

	private String url;

	Browser chromium;

	int instance;

	PopupClientHandler popupClientHandler;

	@Override
	public boolean back() {
		if (canGoBack) {
			ChromiumLib.cefswt_go_back(browser);
			return true;
		}
		return false;
	}

	@Override
	public boolean close() {
		if (disposing != Dispose.No || isDisposed()) {
			return false;
		}
		if (browser == 0) {
			return true;
		}
		boolean closed = false;
		disposing = Dispose.FromClose;
		cef_browser_t.cefswt_close_browser(browser, 0);

		final long t = System.currentTimeMillis();
		long end = t + 10000;
		final Shell shell = chromium.getShell();
		final Display display = shell.getDisplay();
		while (!shell.isDisposed() && System.currentTimeMillis() < end) {
			if (disposing == Dispose.Unload) {
				end += 1000;
			} else if (disposing == Dispose.UnloadClosed) {
				disposing = Dispose.WaitIfClosed;
				end = System.currentTimeMillis() + LOOP * 2;
			} else if (disposing == Dispose.DoIt) {
				closed = true;
				break;
			}
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		if (!closed) {
			disposing = Dispose.No;
		}
		return closed;
	}

	@Override
	public void create(final Composite parent, final int style) {
		ChromiumStatic.initCEF(chromium.getDisplay());

		textVisitor = new StringVisitor(str -> text = str, textReady);

		clientHandler = new ClientHandler(this);
		popupClientHandler = new PopupClientHandler();

		chromium.setBackground(
				parent.getDisplay().getSystemColor(SWT.COLOR_TRANSPARENT));
		paintListener = new PaintListener() {
			@Override
			public void paintControl(final PaintEvent e) {
				chromium.removePaintListener(this);
				createBrowser();
				paintListener = null;
			}
		};
		chromium.addPaintListener(paintListener);
	}

	@Override
	public void createFunction(final BrowserFunction function) {
		created.thenRun(() -> {
			checkBrowser();

			for (final BrowserFunction current : functions.values()) {
				if (current.name.equals(function.name)) {
					deregisterFunction(current);
					break;
				}
			}
			function.index = getNextFunctionIndex();
			registerFunction(function);

			cef_browser_t.cefswt_function(browser, function.name,
					function.index);
		});
	}

	@Override
	public void destroyFunction(final BrowserFunction function) {
		checkBrowser();
		deregisterFunction(function);
	}

	/**
	 * Disposes the browser
	 */
	public void dispose() {
		if (disposing == Dispose.FromDispose || isDisposed()) {
			return;
		}
		final boolean callClose = disposing == Dispose.No;
		disposing = Dispose.FromDispose;
		ChromiumStatic.disposingAny++;
		if (focusListener != null) {
			chromium.removeFocusListener(focusListener);
		}
		focusListener = null;
		if (browser != 0 && callClose) {
			cef_browser_t.cefswt_close_browser(browser, 1);
			waitForClose(Display.getDefault());
		}
	}

	@Override
	public Object evaluate(final String script) throws SWTException {
		if (!jsEnabled) {
			return null;
		}
		if (browser == 0) {
			if (paintListener != null) {
				chromium.removePaintListener(paintListener);
				paintListener = null;
				createBrowser();
			}
		}
		checkBrowser();
		final Object[] ret = new Object[1];
		final EvalReturned callback = (loop, type, valuePtr) -> {
			if (loop == 1) {
				chromium.getDisplay().readAndDispatch();
			} else {
				final String value = ChromiumLib
						.cefswt_cstring_to_java(valuePtr);
				ret[0] = mapType(type, value);
			}
		};
		final Callback callback_cb = new Callback(callback, "invoke",
				void.class, new Type[] { int.class, int.class, long.class });

		final StringBuilder buffer = new StringBuilder("(function() {");
		buffer.append("\n");
		buffer.append(script);
		buffer.append("\n})()");

		final boolean returnSt = cef_browser_t.cefswt_eval(browser,
				buffer.toString(), EVAL++, checkGetAddress(callback_cb));
		disposeCallback(callback_cb);
		if (!returnSt) {
			throw new SWTException("Script that was evaluated failed");
		}
		return ret[0];
	}

	@Override
	public boolean execute(final String script) {
		if (!jsEnabled) {
			return false;
		}
		enableProgress.thenRun(() -> {
			cef_browser_t.cefswt_execute(browser, script);
		});
		return true;
	}

	@Override
	public boolean forward() {
		if (canGoForward) {
			ChromiumLib.cefswt_go_forward(browser);
			return true;
		}
		return false;
	}

	/**
	 * @param browser_id2
	 *            the browser
	 * @param origin_url
	 *            the origin url
	 * @param host
	 *            host to get credentials for
	 * @param port
	 *            port to get credentials for
	 * @param realm
	 *            scope to get credentials for
	 * @param callback
	 *            callback to call with the results
	 * @return 1 on success, 0 on failure
	 */
	public int get_auth_credentials(final long browser_id2,
			final long origin_url, final long host, final int port,
			final long realm, final long callback) {
		if (isDisposed()) {
			return 0;
		}

		final AuthenticationEvent event = new AuthenticationEvent(chromium);
		event.display = chromium.getDisplay();
		event.widget = chromium;
		event.doit = true;
		String protocol = "http";
		try {
			final URL u = new URI(this.url).toURL();
			protocol = u.getProtocol();
		} catch (final MalformedURLException | URISyntaxException e) {
			return 0;
		}
		final String hostStr = host != 0
				? ChromiumLib.cefswt_cefstring_to_java(host)
				: "";
		final String realmStr = realm != 0
				? ChromiumLib.cefswt_cefstring_to_java(realm)
				: null;
		event.location = protocol + "://" + hostStr;
		chromium.getDisplay().syncExec(() -> {
			for (final AuthenticationListener listener : authenticationListeners) {
				listener.authenticate(event);
			}
			if (event.doit && event.user == null && event.password == null) {
				new AuthDialog(chromium.getShell()).open(event, realmStr);
			}
		});
		ChromiumLib.cefswt_auth_callback(callback, event.user, event.password,
				event.doit ? 1 : 0);
		return event.doit ? 1 : 0;
	}

	/**
	 * @return the cef browser instance
	 */
	public int getBrowserId() {
		return browserId;
	}

	@Override
	public String getBrowserType() {
		return "chromium";
	}

	@Override
	public String getText() {
		checkBrowser();
		return text;
	}

	@Override
	public String getUrl() {
		if (browser == 0) {
			if (this.url == null) {
				return "about:blank";
			}
			return getPlainUrl(this.url);
		}
		final long urlPtr = cef_browser_t.cefswt_get_url(browser);
		String cefurl = null;
		if (urlPtr != 0) {
			cefurl = ChromiumLib.cefswt_cstring_to_java(urlPtr);
		}
		if (cefurl == null) {
			cefurl = getPlainUrl(this.url);
		} else {
			cefurl = getPlainUrl(cefurl);
		}
		return cefurl;
	}

	@Override
	public boolean isBackEnabled() {
		return canGoBack;
	}

	@Override
	public boolean isFocusControl() {
		return hasFocus;
	}

	@Override
	public boolean isForwardEnabled() {
		return canGoForward;
	}

	/**
	 * @param frame
	 *            the frame
	 * @param url
	 *            the new url
	 */
	@SuppressWarnings("hiding")
	public void on_address_change(final long frame, final long url) {
		if (isDisposed() || locationListeners == null) {
			return;
		}
		final LocationEvent event = new LocationEvent(chromium);
		event.display = chromium.getDisplay();
		event.widget = chromium;
		event.doit = true;
		event.location = getPlainUrl(ChromiumLib.cefswt_cefstring_to_java(url));
		event.top = ChromiumLib.cefswt_is_main_frame(frame);
		if (!enableProgress.isDone()) {
			return;
		}
		chromium.getDisplay().asyncExec(() -> {
			for (final LocationListener listener : locationListeners) {
				listener.changed(event);
			}
		});
	}

	/**
	 * Callback to be triggered after creation
	 * 
	 * @param browser_id
	 *            the browser
	 */
	public void on_after_created(final long browser_id) {
		if (isDisposed() || visibilityWindowListeners == null) {
			return;
		}
		if (browser_id != 0) {
			this.browser = browser_id;
			this.browserId = cef_browser_t.cefswt_get_id(browser);

			// Register request handlers
			for (final String hostname : requestHandlers.keySet()) {
				ChromiumStatic.getSchemeHandlerFactory()
						.registerSchemeHandler(this, hostname);
			}

			if (this.isPopup == null) {
				final org.eclipse.swt.graphics.Point size = getChromiumSize();
				cef_browser_t.cefswt_resized(browser, size.x, size.y);
			}
			if (this.isPopup != null && this.url != null) {
				doSetUrlPost(browser, url, postData, headers);
			} else if (!"about:blank".equals(this.url)) {
				enableProgress.complete(Boolean.TRUE);
			}
		}
		created.complete(Boolean.TRUE);

		if (ChromiumStatic.browsers.get() == 1) {
			ChromiumStatic.getMessageLoop().start();
		}

		if (isDisposed() || visibilityWindowListeners == null) {
			return;
		}
		final org.eclipse.swt.browser.WindowEvent event = new org.eclipse.swt.browser.WindowEvent(
				chromium);
		event.display = chromium.getDisplay();
		event.widget = chromium;
		event.size = new Point(0, 0);
		event.location = new Point(0, 0);
		if (isPopup != null) {
			event.size = isPopup.size;
			event.location = isPopup.location;
			event.addressBar = isPopup.addressBar;
			event.menuBar = isPopup.menuBar;
			event.statusBar = isPopup.statusBar;
			event.toolBar = isPopup.toolBar;

			if (event.size != null && !event.size.equals(new Point(0, 0))) {
				final Point size = event.size;
				chromium.getShell()
						.setSize(chromium.getShell()
								.computeSize(size.x, size.y));
			}

			for (final VisibilityWindowListener listener : visibilityWindowListeners) {
				listener.show(event);
			}
		}
		try {
			// not sleeping here causes deadlock with multiple window.open
			Thread.sleep(LOOP);
		} catch (final InterruptedException e) {
			// not handled
		}
	}

	/**
	 * @param browser_id2
	 *            the browser
	 * @param frame
	 *            the frame
	 * @param request
	 *            the request url
	 * @return 1 on success, 0 on failure
	 */
	public int on_before_browse(final long browser_id2, final long frame,
			final long request) {
		if (isDisposed() || locationListeners == null) {
			return 0;
		}
		if (ChromiumLib.cefswt_is_main_frame(frame)) {
			final LocationEvent event = new LocationEvent(chromium);
			event.display = chromium.getDisplay();
			event.widget = chromium;
			event.doit = true;
			event.location = getPlainUrl(
					ChromiumLib.cefswt_request_to_java(request));
			try {
				ChromiumStatic.getMessageLoop().pause();
				for (final LocationListener listener : locationListeners) {
					listener.changing(event);
				}
			} finally {
				ChromiumStatic.getMessageLoop().unpause();
			}
			if (!event.doit) {
				enableProgress = new CompletableFuture<>();
			}
			return event.doit ? 0 : 1;
		}
		return 0;
	}

	/**
	 * @param browser_id
	 *            the browser id
	 */
	public void on_before_close(final long browser_id) {
		final Display display = Display.getDefault();
		if (!isDisposed() && closeWindowListeners != null) {
			final org.eclipse.swt.browser.WindowEvent event = new org.eclipse.swt.browser.WindowEvent(
					chromium);
			event.display = display;
			event.widget = chromium;
			for (final CloseWindowListener listener : closeWindowListeners) {
				listener.close(event);
			}
		}

		if (disposing == Dispose.FromClose || disposing == Dispose.Unload
				|| disposing == Dispose.UnloadClosed
				|| disposing == Dispose.WaitIfClosed) {
			disposing = Dispose.DoIt;
		} else if (disposing == Dispose.No) {
			if (chromium != null) {
				disposing = Dispose.FromBrowser;
				chromium.dispose();
			}
		}

		Display.getCurrent().asyncExec(() -> {
			clientHandler.dispose();
			popupClientHandler.dispose();
			textVisitor.dispose();
		});
		this.browser = 0;
		this.chromium = null;
	}

	/**
	 * @param popupFeaturesPtr
	 *            the feature pointer
	 * @param windowInfo
	 *            the window info
	 * @param client
	 *            the cef client
	 * @return 1 on success, 0 on failure
	 */
	public int on_before_popup(final long popupFeaturesPtr,
			final long windowInfo, final long client) {
		if (isDisposed()) {
			return 1;
		}
		if (openWindowListeners == null) {
			return 0;
		}

		final WindowEvent event = new WindowEvent(chromium);

		final cef_popup_features_t popupFeatures = new cef_popup_features_t();
		ChromiumLib.memmove(popupFeatures, popupFeaturesPtr);

		try {
			// not sleeping here causes deadlock with multiple window.open
			Thread.sleep(LOOP);
		} catch (final InterruptedException e) {
			// not handled
		}
		chromium.getDisplay().syncExec(() -> {
			event.display = chromium.getDisplay();
			event.widget = chromium;
			event.required = false;
			event.addressBar = false;
			event.menuBar = false;
			event.statusBar = false;
			event.toolBar = false;
			final int x = popupFeatures.xSet == 1 ? popupFeatures.x : 0;
			final int y = popupFeatures.ySet == 1 ? popupFeatures.y : 0;
			event.location = popupFeatures.xSet == 1 || popupFeatures.ySet == 1
					? new Point(x, y)
					: null;
			final int width = popupFeatures.widthSet == 1 ? popupFeatures.width
					: 0;
			final int height = popupFeatures.heightSet == 1
					? popupFeatures.height
					: 0;
			event.size = popupFeatures.widthSet == 1
					|| popupFeatures.heightSet == 1 ? new Point(width, height)
							: null;

			for (final OpenWindowListener listener : openWindowListeners) {
				listener.open(event);
			}

			if (event.browser != null) {
				if (((Chromium) event.browser.webBrowser).instance == 0) {
					((Chromium) event.browser.webBrowser)
							.createPopup(windowInfo, client, event);
				} else {
					event.required = true;
				}
			} else if (!event.required) {
				instance = ++ChromiumStatic.INSTANCES;
				ChromiumLib.cefswt_set_window_info_parent(windowInfo, client,
						popupClientHandler.get(), 0,
						event.location != null ? event.location.x : 0,
						event.location != null ? event.location.y : 0,
						event.size != null ? event.size.x : 0,
						event.size != null ? event.size.y : 0);
			}
		});

		if (event.browser == null && event.required) {
			return 1;
		}
		if (event.browser != null && event.required) {
			return 1;
		}
		return 0;
	}

	/**
	 * @param message_text
	 *            the message to ask the user (provided by the website)
	 * @param is_reload
	 *            whether this is a reload action rather than a "browse away"
	 *            action
	 * @param callback
	 *            the callback to call with the result
	 * @return 0 on failure, 1 on close
	 */
	public int on_before_unload_dialog(final long message_text,
			final int is_reload, final long callback) {
		if (disposing == Dispose.FromClose) {
			disposing = Dispose.Unload;

			final String msg = ChromiumLib
					.cefswt_cefstring_to_java(message_text);
			openJsDialog(ChromiumLib.JSDIALOGTYPE_PROMPT,
					"Are you sure you want to leave this page?", msg, 0,
					callback);
			disposing = Dispose.UnloadClosed;
			return 1;
		}
		return 0;
	}

	/**
	 * Handles a console message
	 * 
	 * @param id
	 *            this browser id
	 * @param severity
	 *            log severity
	 * @param message
	 *            message
	 * @param source
	 *            source file
	 * @param line
	 *            source line
	 * @return 1 if the message was handled externally, 0 otherwise
	 */
	public int on_console_message(final long id, final int severity,
			final long message, final long source, final int line) {
		if (consoleListener != null) {
			final String msg = message == 0 ? ""
					: ChromiumLib.cefswt_cefstring_to_java(message);
			final String src = source == 0 ? ""
					: ChromiumLib.cefswt_cefstring_to_java(source);
			consoleListener.onConsoleMessage(severity, msg, src, line);
			// Return true to stop the message from being output to the console.
			return 1;
		}
		return 0;
	}

	/**
	 * Triggered when a Dialog is closed
	 */
	public void on_dialog_closed() {
		if (disposing == Dispose.Unload) {
			disposing = Dispose.UnloadClosed;
		}
	}

	/**
	 * Triggered when the browser gets focused
	 */
	public void on_got_focus() {
		if (!isDisposed()) {
			hasFocus = true;
			if (!isDisposed()
					&& chromium.getDisplay().getFocusControl() != null) {
				chromium.setFocus();
			}
			browserFocus(true);
		}
	}

	/**
	 * Triggered when a JS Dialog is opened
	 * 
	 * @param origin_url
	 *            the origin url
	 * @param dialog_type
	 *            the type of dialog
	 * @param message_text
	 *            the message text
	 * @param default_prompt_text
	 *            the default prompt text
	 * @param callback
	 *            the callback to call
	 * @return 0 on failure, 1 on success
	 */
	public int on_jsdialog(final long origin_url, final int dialog_type,
			final long message_text, final long default_prompt_text,
			final long callback) {
		if (isDisposed()) {
			return 0;
		}

		@SuppressWarnings("hiding")
		final String url = ChromiumLib.cefswt_cefstring_to_java(origin_url);
		final String title = getPlainUrl(url);
		final String msg = ChromiumLib.cefswt_cefstring_to_java(message_text);
		openJsDialog(dialog_type, title, msg, default_prompt_text, callback);
		return 1;
	}

	/**
	 * Called when the loading state of the browser changes
	 * 
	 * @param isLoading
	 *            whether the browser is currently loading a new web page
	 * @param canUserGoBack
	 *            whether the user can go back
	 * @param canUserGoForward
	 *            whether the user can go forward
	 */
	public void on_loading_state_change(final int isLoading,
			final int canUserGoBack, final int canUserGoForward) {
		canGoBack = canUserGoBack == 1;
		canGoForward = canUserGoForward == 1;
		if (isDisposed() || progressListeners == null) {
			return;
		}
		if (isLoading == 0) {
			for (final BrowserFunction function : functions.values()) {
				if (function.index != 0) {
					cef_browser_t.cefswt_function(browser, function.name,
							function.index);
				}
			}
		}
		updateText();
		if (isPopup != null) {
			textReady.thenRun(() -> enableProgress.complete(Boolean.TRUE));
		} else if (!enableProgress.isDone() && isLoading == 0) {
			textReady.thenRun(() -> enableProgress.complete(Boolean.TRUE));
			return;
		} else if (!enableProgress.isDone()) {
			return;
		}
		final ProgressEvent event = new ProgressEvent(chromium);
		event.display = chromium.getDisplay();
		event.widget = chromium;
		event.current = isLoading == 1 ? 1 : MAX_PROGRESS;
		event.total = MAX_PROGRESS;
		if (isLoading == 1) {
			for (final ProgressListener listener : progressListeners) {
				listener.changed(event);
			}
		} else {
			textReady.thenRun(() -> {
				chromium.getDisplay().asyncExec(() -> {
					for (final ProgressListener listener : progressListeners) {
						listener.completed(event);
					}
				});
			});
		}
	}

	/**
	 * Called when a process message is received (e.g. due to a Javascript
	 * result being available)
	 * 
	 * @param source
	 *            the source process
	 * @param processMessage
	 *            the message
	 * @return 0 on failure 1 on success
	 */
	public int on_process_message_received(final int source,
			final long processMessage) {
		if (source != CEFFactory.PID_RENDERER || !jsEnabled
				|| chromium == null) {
			return 0;
		}
		final FunctionSt fn = new FunctionSt();
		ChromiumLib.cefswt_function_id(processMessage, fn);
		final int id = fn.id;
		if (id < 0) {
			return 0;
		}
		final int argsSize = fn.args;
		final Object[] args = new Object[argsSize];
		for (int i = 0; i < argsSize; i++) {
			final int arg = i;
			final EvalReturned callback = (loop, type, valuePtr) -> {
				if (loop == 0) {
					final String value = ChromiumLib
							.cefswt_cstring_to_java(valuePtr);
					args[arg] = mapType(type, value);
				}
			};
			final Callback callback_cb = new Callback(callback, "invoke",
					void.class,
					new Type[] { int.class, int.class, long.class });
			ChromiumLib.cefswt_function_arg(processMessage, i,
					checkGetAddress(callback_cb));
			disposeCallback(callback_cb);
		}
		@SuppressWarnings("boxing")
		final Object ret = functions.get(id).function(args);

		final Object[] returnPair = convertType(ret);
		final ReturnType returnType = (ReturnType) returnPair[0];
		final String returnStr = (String) returnPair[1];
		ChromiumLib.cefswt_function_return(browser, id, fn.port,
				returnType.intValue(), returnStr);

		return 1;
	}

	/**
	 * Triggered when the focus is set
	 * 
	 * @return 1 on success, 0 on failure
	 */
	public int on_set_focus() {
		if (ignoreFirstFocus) {
			ignoreFirstFocus = false;
			return 1;
		}
		return 0;
	}

	/**
	 * Triggered when a status text changes
	 * 
	 * @param status
	 *            the status text
	 */
	public void on_status_message(final long status) {
		if (isDisposed() || statusTextListeners == null) {
			return;
		}
		final String str = status == 0 ? ""
				: ChromiumLib.cefswt_cefstring_to_java(status);
		final StatusTextEvent event = new StatusTextEvent(chromium);
		event.display = chromium.getDisplay();
		event.widget = chromium;
		event.text = str;
		for (final StatusTextListener listener : statusTextListeners) {
			listener.changed(event);
		}
	}

	/**
	 * Triggered on taking the focus
	 * 
	 * @param next
	 *            whether to take the current or next tab
	 */
	public void on_take_focus(final int next) {
		hasFocus = false;
		Control[] tabOrder = chromium.getParent().getTabList();
		if (tabOrder.length == 0) {
			tabOrder = chromium.getParent().getChildren();
		}
		final int indexOf = Arrays.asList(tabOrder).indexOf(chromium);
		if (indexOf != -1) {
			final int newIndex = next == 1 ? indexOf + 1 : indexOf - 1;
			if (newIndex > 0 && newIndex < tabOrder.length
					&& !tabOrder[newIndex].isDisposed()) {
				tabOrder[newIndex].setFocus();
				return;
			}
		}
		if (!isDisposed() && !chromium.getParent().isDisposed()) {
			chromium.getParent().setFocus();
		}
	}

	/**
	 * Called when a new title is set
	 * 
	 * @param title
	 *            the new title
	 */
	public void on_title_change(final long title) {
		if (isDisposed() || titleListeners == null) {
			return;
		}
		final String full_str = ChromiumLib.cefswt_cefstring_to_java(title);
		final String str = getPlainUrl(full_str);
		final TitleEvent event = new TitleEvent(chromium);
		event.display = chromium.getDisplay();
		event.widget = chromium;
		event.title = str;
		for (final TitleListener listener : titleListeners) {
			listener.changed(event);
		}
	}

	/**
	 * @param name
	 *            the hostname
	 * @return the custom handler for the given hostname or null
	 */
	public ResourceHandler onRequestCustomHandler(final String name) {
		if (!requestHandlers.containsKey(name)) {
			return null;
		}
		final RequestHandler requestHandler = requestHandlers.get(name);
		return new ResourceHandler(requestHandler);
	}

	@Override
	public void refresh() {
		jsEnabled = jsEnabledOnNextPage;
		if (browser != 0) {
			cef_browser_t.cefswt_reload(browser);
		}
	}

	@Override
	public void registerRequestHandler(final String hostname,
			final RequestHandler handler) {
		this.requestHandlers.put(hostname, handler);
	}

	/**
	 * Opens the context menu
	 * 
	 * @param callback
	 *            the callback
	 * @return 0 on failure, 1 on success
	 */
	public int run_context_menu(final long callback) {
		if (chromium.getMenu() != null) {
			chromium.getMenu().setVisible(true);
			ChromiumLib.cefswt_context_menu_cancel(callback);
			return 1;
		}
		return 0;
	}

	@Override
	public void setBrowser(final Browser browser) {
		this.chromium = browser;
	}

	@Override
	public boolean setText(final String html, final boolean trusted) {
		if (html.contains("file:/")) { // file:/ resources not supported with
										// data:text
			try {
				final Path tmp = Files.createTempFile(SET_TEXT_URL, ".html");
				Files.write(tmp, html.getBytes());
				tmp.toFile().deleteOnExit();
				return setUrl(tmp.toUri().toString(), null, null);
			} catch (final IOException e) {
				// IMPROVE: Can we handle this in a useful manner?
			}
		}
		final String texturl = DATA_TEXT_URL
				+ Base64.getEncoder().encodeToString(html.getBytes());
		return setUrl(texturl, null, null);
	}

	@Override
	public boolean setUrl(final String url, final String postData,
			final String[] headers) {
		// if not yet created will be used when created
		this.url = url;
		this.postData = postData;
		this.headers = headers;
		jsEnabled = jsEnabledOnNextPage;
		if (!isDisposed() && browser != 0) {
			doSetUrl(url, postData, headers);
		}
		return true;
	}

	@Override
	public void stop() {
		if (browser != 0) {
			cef_browser_t.cefswt_stop(browser);
		}
	}

	private synchronized void checkBrowser() {
		if (browser == 0) {
			SWT.error(SWT.ERROR_WIDGET_DISPOSED);
		}
	}

	private Object[] convertType(final Object ret) {
		ReturnType returnType = ReturnType.Error;
		String returnStr = "";
		if (ret == null) {
			returnType = ReturnType.Null;
			returnStr = "null";
		} else if (Boolean.class.isInstance(ret)) {
			returnType = ReturnType.Bool;
			returnStr = Boolean.TRUE.equals(ret) ? "1" : "0";
		} else if (Number.class.isInstance(ret)) {
			returnType = ReturnType.Double;
			returnStr = NumberFormat.getInstance(Locale.US).format(ret);
		} else if (String.class.isInstance(ret)) {
			returnType = ReturnType.Str;
			returnStr = ret.toString();
		} else if (ret.getClass().isArray()) {
			returnType = ReturnType.Array;
			final StringBuilder buffer = new StringBuilder();
			buffer.append("\"");
			for (int i = 0; i < Array.getLength(ret); i++) {
				if (i > 0) {
					buffer.append(";");
				}
				final Object[] arrayElem = convertType(Array.get(ret, i));
				buffer.append("'");
				buffer.append(((ReturnType) arrayElem[0]).intValue());
				buffer.append(",");
				buffer.append((String) arrayElem[1]);
				buffer.append("'");
			}
			buffer.append("\"");
			returnStr = buffer.toString();
		} else {
			returnStr = "Unsupported return type " + ret.getClass().getName();
		}
		return new Object[] { returnType, returnStr };
	}

	@SuppressWarnings("boxing")
	private void createBrowser() {
		if (this.url == null) {
			this.url = "about:blank";
		}
		prepareBrowser();
		final Display display = chromium.getDisplay();
		final Color bg = chromium.getBackground();
		final Color bgColor = bg != null ? bg
				: display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
		final int cefBgColor = cefColor(bgColor.getAlpha(), bgColor.getRed(),
				bgColor.getGreen(), bgColor.getBlue());

		final org.eclipse.swt.graphics.Point size = getChromiumSize();

		instance = ++ChromiumStatic.INSTANCES;
		ChromiumStatic.instances.put(instance, this);
		ChromiumLib.cefswt_create_browser(hwnd, url, clientHandler.get(),
				size.x, size.y, jsEnabledOnNextPage ? 1 : 0, cefBgColor);
	}

	@SuppressWarnings("boxing")
	private void createPopup(final long windowInfo, final long client,
			final WindowEvent event) {
		if (paintListener != null) {
			chromium.removePaintListener(paintListener);
			paintListener = null;
		} else {
			// TODO: destroy browser first?
			ChromiumStatic.instances.remove(instance);
		}
		instance = ++ChromiumStatic.INSTANCES;
		ChromiumStatic.instances.put(instance, this);
		isPopup = event;

		prepareBrowser();
		final long popupHandle = hwnd;
		final Point size = new Point(0, 0);
		ChromiumLib.cefswt_set_window_info_parent(windowInfo, client,
				clientHandler.get(), popupHandle, 0, 0, size.x, size.y);
	}

	@SuppressWarnings("hiding")
	private CompletableFuture<Void> doSetUrl(final String url,
			final String postData, final String[] headers) {
		return enableProgress
				.thenRun(() -> doSetUrlPost(browser, url, postData, headers));
	}

	private Point getChromiumSize() {
		int zoom = DPIUtil.getDeviceZoom();
		Point size = Point.OfFloat.from(chromium.getSize());
		size.x = DPIUtil.pointToPixel(size.x, zoom);
		size.y = DPIUtil.pointToPixel(size.y, zoom);
		return size;
	}

	private Object mapType(final int type, final String value)
			throws SWTException {
		if (type == ReturnType.Error.intValue()) {
			if ((SWT.ERROR_INVALID_RETURN_VALUE + "").equals(value)) {
				throw new SWTException(SWT.ERROR_INVALID_RETURN_VALUE);
			}
			throw new SWTException(SWT.ERROR_FAILED_EVALUATE, value);
		} else if (type == ReturnType.Null.intValue()) {
			return null;
		} else if (type == ReturnType.Bool.intValue()) {
			return "1".equals(value) ? Boolean.TRUE : Boolean.FALSE;
		} else if (type == ReturnType.Double.intValue()) {
			return Double.valueOf(value);
		} else if (type == ReturnType.Array.intValue()) {
			final String value_unquoted = value.substring(1,
					value.length() - 1);
			final String[] elements = value_unquoted
					.split(";(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
			final Object[] array = new Object[elements.length];
			for (int i = 0; i < array.length; i++) {
				final String elemUnquoted = elements[i].substring(1,
						elements[i].length() - 1);
				final String[] parts = elemUnquoted
						.split(",(?=(?:[^']*'[^']*')*[^']*$)", 2);
				final ReturnType elemType = CEFFactory.ReturnType
						.from(parts[0]);
				final Object elemValue = mapType(elemType.intValue(), parts[1]);
				array[i] = elemValue;
			}
			return array;
		} else {
			return value;
		}
	}

	@SuppressWarnings("boxing")
	private void openJsDialog(final int dialog_type, final String title,
			final String msg, final long default_prompt_text,
			final long callback) {
		int style = SWT.ICON_WORKING;
		switch (dialog_type) {
			case ChromiumLib.JSDIALOGTYPE_ALERT:
				style = SWT.ICON_INFORMATION;
				break;
			case ChromiumLib.JSDIALOGTYPE_CONFIRM:
				style = SWT.ICON_WARNING;
				break;
			case ChromiumLib.JSDIALOGTYPE_PROMPT:
				style = SWT.ICON_QUESTION | SWT.YES | SWT.NO;
				break;
			default:
				style = SWT.ICON_QUESTION;
				break;
		}
		final Consumer<Integer> close = open -> {
			final int r = open == SWT.OK || open == SWT.YES ? 1 : 0;
			ChromiumLib.cefswt_dialog_close(callback, r, default_prompt_text);
			chromium.getShell().forceActive();
		};
		if (!"test".equals(System.getProperty("swt.chromium.dialogs", ""))) {
			final MessageBox box = new MessageBox(chromium.getShell(), style);
			box.setText(title);
			box.setMessage(msg);
			final int open = box.open();
			close.accept(open);
		} else {
			@SuppressWarnings("unchecked")
			final CompletableFuture<Integer> f = (CompletableFuture<Integer>) chromium
					.getData("swt.chromium.dialogs");
			if (f != null) {
				f.thenAccept(close);
				chromium.setData("swt.chromium.dialogs", null);
				while (!f.isDone()) {
					if (!chromium.getDisplay().readAndDispatch()) {
						chromium.getDisplay().sleep();
					}
				}
			}
		}
	}

	private void prepareBrowser() {
		hwnd = getHandle(chromium);

		chromium.addDisposeListener(e -> dispose());
		focusListener = new CEFFocusListener();
		chromium.addFocusListener(focusListener);

		chromium.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(final ControlEvent e) {
				if (!isDisposed() && browser != 0) {
					final Point size = getChromiumSize();
					cef_browser_t.cefswt_resized(browser, size.x, size.y);
				}
			}
		});
	}

	private void updateText() {
		if (browser != 0 && !isDisposed() && disposing == Dispose.No) {
			cef_browser_t.cefswt_get_text(browser, textVisitor.get());
		}
	}

	private void waitForClose(final Display display) {
		if (display == null || display.isDisposed()) {
			return;
		}
		display.asyncExec(() -> {
			if (browser != 0) {
				waitForClose(display);
			}
		});
	}

	protected void browserFocus(final boolean set) {
		if (!isDisposed() && browser != 0) {
			final long parent = Display.getDefault().getActiveShell() == null
					? 0
					: getHandle(chromium.getParent());
			if (chromium.getDisplay().getActiveShell() != chromium.getShell()) {
				return;
			}

			cef_browser_t.cefswt_set_focus(browser, set, parent);
		}
	}

	boolean isDisposed() {
		return chromium == null || chromium.isDisposed();
	}

}
