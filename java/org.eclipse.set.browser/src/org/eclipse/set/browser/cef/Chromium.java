/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.set.browser.cef;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.eclipse.set.browser.Browser;
import org.eclipse.set.browser.BrowserFunction;
import org.eclipse.set.browser.OpenWindowListener;
import org.eclipse.set.browser.WebBrowser;
import org.eclipse.set.browser.WindowEvent;
import org.eclipse.set.browser.bin.CEFLibrary;
import org.eclipse.set.browser.bin.lib.ChromiumLib;
import org.eclipse.set.browser.bin.lib.FunctionSt;
import org.eclipse.set.browser.bin.lib.cef_app_t;
import org.eclipse.set.browser.bin.lib.cef_browser_process_handler_t;
import org.eclipse.set.browser.bin.lib.cef_client_t;
import org.eclipse.set.browser.bin.lib.cef_context_menu_handler_t;
import org.eclipse.set.browser.bin.lib.cef_cookie_visitor_t;
import org.eclipse.set.browser.bin.lib.cef_display_handler_t;
import org.eclipse.set.browser.bin.lib.cef_focus_handler_t;
import org.eclipse.set.browser.bin.lib.cef_jsdialog_handler_t;
import org.eclipse.set.browser.bin.lib.cef_life_span_handler_t;
import org.eclipse.set.browser.bin.lib.cef_load_handler_t;
import org.eclipse.set.browser.bin.lib.cef_popup_features_t;
import org.eclipse.set.browser.bin.lib.cef_request_handler_t;
import org.eclipse.set.browser.bin.lib.cef_string_visitor_t;
import org.eclipse.set.browser.cef.CEFFactory.ReturnType;
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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class Chromium extends WebBrowser {
	public static interface EvalReturned {
		void invoke(int loop, int type, long value);
	}

	private final class CefFocusListener implements FocusListener {
		private boolean enabled = true;

		@Override
		public void focusGained(final FocusEvent e) {
			if (!enabled) {
				return;
			}
			// debugPrint("focusGained");
			browserFocus(true);
		}

		@Override
		public void focusLost(final FocusEvent e) {
			if (!enabled) {
				return;
			}
			enabled = false;
			// debugPrint("focusLost");
			browserFocus(false);
			// System.out.println(Display.getDefault().getFocusControl());
			enabled = true;
		}
	}

	class AuthDialog extends Dialog {

		public AuthDialog(final Shell parent) {
			super(parent);
		}

		public void open(final AuthenticationEvent authEvent,
				final String realm) {
			final Shell parent = getParent();
			final Shell shell = new Shell(parent,
					SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
			shell.setText("Authentication Required");
			final GridLayout layout = new GridLayout(2, false);
			layout.marginHeight = 10;
			layout.marginWidth = 10;
			shell.setLayout(layout);

			final Label info = new Label(shell, SWT.WRAP);
			final StringBuilder infoText = new StringBuilder(
					authEvent.location);
			infoText.append(" is requesting you username and password.");
			if (realm != null) {
				infoText.append(" The site says: \"").append(realm)
						.append("\"");
			}
			info.setText(infoText.toString());
			info.setLayoutData(
					new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

			final Label label1 = new Label(shell, SWT.NONE);
			label1.setText("User Name: ");
			final Text username = new Text(shell, SWT.SINGLE | SWT.BORDER);
			username.setLayoutData(
					new GridData(SWT.FILL, SWT.CENTER, true, false));

			final Label label2 = new Label(shell, SWT.NONE);
			label2.setText("Password: ");
			final Text password = new Text(shell, SWT.SINGLE | SWT.BORDER);
			password.setLayoutData(
					new GridData(SWT.FILL, SWT.CENTER, true, false));
			password.setEchoChar('*');

			final Composite bar = new Composite(shell, SWT.NONE);
			bar.setLayoutData(
					new GridData(SWT.END, SWT.END, false, true, 2, 1));
			bar.setLayout(new GridLayout(2, true));

			final Button cancelButton = new Button(bar, SWT.PUSH);
			cancelButton.setText("Cancel");
			cancelButton.addListener(SWT.Selection, event -> {
				authEvent.doit = false;
				shell.close();
			});
			final GridData cancelData = new GridData(SWT.CENTER, SWT.END, false,
					false);
			cancelData.widthHint = 80;
			cancelButton.setLayoutData(cancelData);

			final Button okButton = new Button(bar, SWT.PUSH);
			okButton.setText("Ok");
			okButton.addListener(SWT.Selection, event -> {
				authEvent.user = username.getText();
				authEvent.password = password.getText();
				shell.close();
			});
			final GridData okData = new GridData(SWT.CENTER, SWT.END, false,
					false);
			okData.minimumWidth = SWT.DEFAULT;
			okData.widthHint = 80;
			okButton.setLayoutData(okData);

			shell.pack();
			shell.open();
			final Display display = parent.getDisplay();
			while (!shell.isDisposed()) {
				if (!display.readAndDispatch()) {
					display.sleep();
				}
			}
		}
	}

	enum Dispose {
		DoIt, FromBrowser, FromClose, FromDispose, No, Unload, UnloadClosed, WaitIfClosed,
	}

	// private static CompletableFuture<Boolean> cefInitilized;
	private static cef_app_t app;
	private static cef_browser_process_handler_t browserProcessHandler;
	private static AtomicInteger browsers = new AtomicInteger(0);
	private static String cefPath;
	private static String cefrustPath;

	private static final String CEFVERSION = "3071";

	private static cef_client_t clientHandler;
	private static cef_context_menu_handler_t contextMenuHandler;
	private static CompletableFuture<Boolean> cookieVisited;
	private static cef_cookie_visitor_t cookieVisitor;
	private static final String DATA_TEXT_URL = "data:text/html;base64,";
	private static final boolean debug = true;
	private static cef_display_handler_t displayHandler;
	private static int disposingAny = 0;
	private static int EVAL = 1;
	private static cef_focus_handler_t focusHandler;
	private static Map<Integer, Chromium> instances = new HashMap<>();
	private static int INSTANCES = 0;
	private static final String JNI_LIB_V = "swt-chromium-win64";
	private static cef_jsdialog_handler_t jsDialogHandler;
	private static Object lib;
	private static cef_life_span_handler_t lifeSpanHandler;
	private static cef_load_handler_t loadHandler;

	private static final int LOOP = 75;
	private static boolean loopDisable;
	private static Runnable loopWork;
	private static final int MAX_PROGRESS = 100;
	private static cef_client_t popupClientHandler;
	private static int popupHandlers = 0;
	private static cef_life_span_handler_t popupLifeSpanHandler;
	private static boolean pumpDisable;
	private static cef_request_handler_t requestHandler;
	private static final String SET_TEXT_URL = "swt.chromium.setText.";
	private static final String SHARED_LIB_V = "chromium_swt";

	private static boolean shuttindDown;
	private static final String VERSION = "4936r26";
	static Runnable loopWorkRunnable = () -> {
		final Display display = Display.getCurrent();
		if (display == null || display
				.isDisposed() /* || display.getActiveShell() != getShell() */) {
			// System.err.println("Ignore do_message_loop_work due inactive
			// shell");
			return;
		}
		// debug("WORK PUMP");
		safe_loop_work("pump");
	};
	static {
		lib = loadLib();
	}

	/**
	 * Re-initializing CEF3 is not supported due to the use of globals. This
	 * must be called on app exit.
	 */
	public static void shutdown() {
		final String platform = SWT.getPlatform();
		if ("cocoa".equals(platform)) {
			// ignore on mac, will shutdown with shutdown hook, otherwise it
			// crashes.
			return;
		}
		debug("Shutdown from API");
		internalShutdown();
	}

	private static void debug(final String log) {
		if (debug) {
			System.out.println("J:" + log);
		}
	}

	// single loop for all browsers
	private static void doMessageLoop(final Display display) {
		loopWork = () -> {
			if (lib != null && !display.isDisposed()) {
				// debug("WORK CLOCK");
				safe_loop_work("timer");
				display.timerExec(LOOP * 2, loopWork);
			} else {
				debug("STOPPING MSG LOOP");
			}
		};
		display.timerExec(LOOP, loopWork);
	}

	private static void doSetUrlPost(final long browser, final String url,
			final String postData, final String[] headers) {
		final byte[] bytes = postData != null
				? postData.getBytes(Charset.forName("ASCII"))
				: null;
		final int bytesLength = postData != null ? bytes.length : 0;
		final int headersLength = headers != null ? headers.length : 0;
		final String joinHeaders = headers == null ? null
				: String.join("::", headers);
		ChromiumLib.cefswt_load_url(browser, url, bytes, bytesLength,
				joinHeaders, headersLength);
	}

	static private synchronized void freeAll(final Display display) {
		if (!instances.isEmpty()) {
			System.err.println("freeing all handlers, but there are instances");
		}
		if (clientHandler != null) {
			C.free(clientHandler.ptr);
			disposeCallback(clientHandler.get_context_menu_handler_cb);
			disposeCallback(clientHandler.get_display_handler_cb);
			disposeCallback(clientHandler.get_focus_handler_cb);
			disposeCallback(clientHandler.get_jsdialog_handler_cb);

			final Callback get_life_span_handler_cb = clientHandler.get_life_span_handler_cb;
			final Callback get_request_handler_cb = clientHandler.get_request_handler_cb;
			disposeCallback(get_life_span_handler_cb);
			disposeCallback(get_request_handler_cb);
			disposeCallback(clientHandler.get_load_handler_cb);
			disposeCallback(clientHandler.on_process_message_received_cb);
			clientHandler = null;
		}
		if (focusHandler != null) {
			C.free(focusHandler.ptr);
			disposeCallback(focusHandler.on_got_focus_cb);
			disposeCallback(focusHandler.on_set_focus_cb);
			disposeCallback(focusHandler.on_take_focus_cb);
			focusHandler = null;
		}
		if (lifeSpanHandler != null) {
			disposeCallback(lifeSpanHandler.do_close_cb);
			disposeCallback(lifeSpanHandler.on_after_created_cb);
			disposeCallback(lifeSpanHandler.on_before_close_cb);
			disposeCallback(lifeSpanHandler.on_before_popup_cb);
			C.free(lifeSpanHandler.ptr);
			lifeSpanHandler = null;
		}
		if (loadHandler != null) {
			disposeCallback(loadHandler.on_loading_state_change_cb);
			C.free(loadHandler.ptr);
			loadHandler = null;
		}
		if (displayHandler != null) {
			disposeCallback(displayHandler.on_address_change_cb);
			disposeCallback(displayHandler.on_status_message_cb);
			disposeCallback(displayHandler.on_title_change_cb);
			C.free(displayHandler.ptr);
			displayHandler = null;
		}
		if (requestHandler != null) {
			disposeCallback(requestHandler.on_before_browse_cb);
			disposeCallback(requestHandler.get_auth_credentials_cb);
			C.free(requestHandler.ptr);
			requestHandler = null;
		}
		if (jsDialogHandler != null) {
			disposeCallback(jsDialogHandler.on_jsdialog_cb);
			disposeCallback(jsDialogHandler.on_dialog_closed_cb);
			disposeCallback(jsDialogHandler.on_before_unload_dialog_cb);
			C.free(jsDialogHandler.ptr);
			jsDialogHandler = null;
		}
		if (contextMenuHandler != null) {
			disposeCallback(contextMenuHandler.run_context_menu_cb);
			C.free(contextMenuHandler.ptr);
			contextMenuHandler = null;
		}
		if (popupClientHandler != null) {
			disposeCallback(popupClientHandler.get_life_span_handler_cb);
			C.free(popupClientHandler.ptr);
			popupClientHandler = null;
		}

		debug("all dipsosed");
	}

	private static void freeDelayed(final long ptr) {
		Display.getDefault().asyncExec(() -> C.free(ptr));
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

	private static void internalShutdown() {
		if (lib == null || app == null) {
			return;
		}
		if (browsers.get() == 0) {
			debug("shutting down CEF on exit from thread "
					+ Thread.currentThread().getName());
			freeAll(null);
			ChromiumLib.cefswt_shutdown();

			if (cookieVisitor != null) {
				disposeCallback(cookieVisitor.visit_cb);
				C.free(cookieVisitor.ptr);
				cookieVisitor = null;
			}

			disposeCallback(app.get_browser_process_handler_cb);
			C.free(app.ptr);
			app = null;

			disposeCallback(
					browserProcessHandler.on_schedule_message_pump_work_cb);
			C.free(browserProcessHandler.ptr);
			browserProcessHandler = null;
			// MemoryIO.getInstance().freeMemory(Struct.getMemory(app).address());
			debug("after shutting down CEF");
		} else if (!shuttindDown) {
			shuttindDown = true;
			debug("delaying shutdown due browsers not disposed yet");
		}
	}

	private static Object loadLib() {
		CEFLibrary.loadLibraries();
		cefrustPath = CEFLibrary.getJNIPath();
		cefPath = CEFLibrary.getCEFPath();
		setupCookies();

		return new Object();
	}

	private static void restartLoop(final Display display, final int ms) {
		if (loopWork != null) {
			display.timerExec(-1, loopWork);
			display.timerExec(LOOP + ms, loopWork);
		}
	}

	private static void safe_loop_work(final String from) {
		if (browsers.get() > 0 && !loopDisable) {
			// debug("safe_loop_work "+from);
			if (ChromiumLib.cefswt_do_message_loop_work() == 0) {
				System.err.println("error looping chromium");
			}
			if (pumpDisable == true) {
				pumpDisable = false;
			}
		}
	}

	private static Chromium safeGeInstance(final int id) {
		final Chromium c = instances.get(id);
		if (c == null) {
			throw new SWTError("Wrong chromium id " + id);
		}
		return c;
	}

	private static void set_client_handler() {
		clientHandler = CEFFactory.newClient();
		set_focus_handler();
		set_life_span_handler();
		set_load_handler();
		set_display_handler();
		set_request_handler();
		set_jsdialog_handler();
		set_context_menu_handler();
		clientHandler.on_process_message_received_cb = new Callback(
				Chromium.class, "on_process_message_received", int.class,
				new Type[] { long.class, long.class, int.class, long.class });
		clientHandler.on_process_message_received = checkGetAddress(
				clientHandler.on_process_message_received_cb);

		clientHandler.ptr = C.malloc(cef_client_t.sizeof);
		ChromiumLib.memmove(clientHandler.ptr, clientHandler,
				cef_client_t.sizeof);
	}

	private static void set_context_menu_handler() {
		contextMenuHandler = CEFFactory.newContextMenuHandler();
		contextMenuHandler.run_context_menu_cb = new Callback(Chromium.class,
				"run_context_menu", int.class,
				new Type[] { long.class, long.class, long.class, long.class,
						long.class, long.class });
		contextMenuHandler.run_context_menu = checkGetAddress(
				contextMenuHandler.run_context_menu_cb);

		clientHandler.get_context_menu_handler_cb = new Callback(Chromium.class,
				"get_context_menu_handler", long.class,
				new Type[] { long.class });
		clientHandler.get_context_menu_handler = checkGetAddress(
				clientHandler.get_context_menu_handler_cb);
		contextMenuHandler.ptr = C.malloc(cef_context_menu_handler_t.sizeof);
		ChromiumLib.memmove(contextMenuHandler.ptr, contextMenuHandler,
				cef_context_menu_handler_t.sizeof);
	}

	private static void set_display_handler() {
		displayHandler = CEFFactory.newDisplayHandler();
		displayHandler.on_title_change_cb = new Callback(Chromium.class,
				"on_title_change", void.class,
				new Type[] { long.class, long.class, long.class });
		displayHandler.on_title_change = checkGetAddress(
				displayHandler.on_title_change_cb);
		displayHandler.on_address_change_cb = new Callback(Chromium.class,
				"on_address_change", void.class,
				new Type[] { long.class, long.class, long.class, long.class });
		displayHandler.on_address_change = checkGetAddress(
				displayHandler.on_address_change_cb);
		displayHandler.on_status_message_cb = new Callback(Chromium.class,
				"on_status_message", void.class,
				new Type[] { long.class, long.class, long.class });
		displayHandler.on_status_message = checkGetAddress(
				displayHandler.on_status_message_cb);

		clientHandler.get_display_handler_cb = new Callback(Chromium.class,
				"get_display_handler", long.class, new Type[] { long.class });
		clientHandler.get_display_handler = checkGetAddress(
				clientHandler.get_display_handler_cb);
		displayHandler.ptr = C.malloc(cef_display_handler_t.sizeof);
		ChromiumLib.memmove(displayHandler.ptr, displayHandler,
				cef_display_handler_t.sizeof);
	}

	private static void set_focus_handler() {
		focusHandler = CEFFactory.newFocusHandler();
		focusHandler.on_got_focus_cb = new Callback(Chromium.class,
				"on_got_focus", void.class,
				new Type[] { long.class, long.class });
		focusHandler.on_got_focus = checkGetAddress(
				focusHandler.on_got_focus_cb);
		focusHandler.on_set_focus_cb = new Callback(Chromium.class,
				"on_set_focus", int.class,
				new Type[] { long.class, long.class, int.class });
		focusHandler.on_set_focus = checkGetAddress(
				focusHandler.on_set_focus_cb);
		focusHandler.on_take_focus_cb = new Callback(Chromium.class,
				"on_take_focus", void.class,
				new Type[] { long.class, long.class, int.class });
		focusHandler.on_take_focus = checkGetAddress(
				focusHandler.on_take_focus_cb);

		clientHandler.get_focus_handler_cb = new Callback(Chromium.class,
				"get_focus_handler", long.class, new Type[] { long.class });
		clientHandler.get_focus_handler = checkGetAddress(
				clientHandler.get_focus_handler_cb);
		focusHandler.ptr = C.malloc(cef_focus_handler_t.sizeof);
		ChromiumLib.memmove(focusHandler.ptr, focusHandler,
				cef_focus_handler_t.sizeof);
	}

	private static void set_jsdialog_handler() {
		jsDialogHandler = CEFFactory.newJsDialogHandler();
		if (useSwtDialogs()) {
			jsDialogHandler.on_jsdialog_cb = new Callback(Chromium.class,
					"on_jsdialog", int.class,
					new Type[] { long.class, long.class, long.class, int.class,
							long.class, long.class, long.class, int.class });
			jsDialogHandler.on_jsdialog = checkGetAddress(
					jsDialogHandler.on_jsdialog_cb);
		}
		jsDialogHandler.on_dialog_closed_cb = new Callback(Chromium.class,
				"on_dialog_closed", void.class,
				new Type[] { long.class, long.class });
		jsDialogHandler.on_dialog_closed = checkGetAddress(
				jsDialogHandler.on_dialog_closed_cb);
		jsDialogHandler.on_before_unload_dialog_cb = new Callback(
				Chromium.class, "on_before_unload_dialog", int.class,
				new Type[] { long.class, long.class, long.class, int.class,
						long.class });
		jsDialogHandler.on_before_unload_dialog = checkGetAddress(
				jsDialogHandler.on_before_unload_dialog_cb);

		clientHandler.get_jsdialog_handler_cb = new Callback(Chromium.class,
				"get_jsdialog_handler", long.class, new Type[] { long.class });
		clientHandler.get_jsdialog_handler = checkGetAddress(
				clientHandler.get_jsdialog_handler_cb);
		jsDialogHandler.ptr = C.malloc(cef_jsdialog_handler_t.sizeof);
		ChromiumLib.memmove(jsDialogHandler.ptr, jsDialogHandler,
				cef_jsdialog_handler_t.sizeof);
	}

	private static void set_life_span_handler() {
		lifeSpanHandler = CEFFactory.newLifeSpanHandler();
		lifeSpanHandler.on_before_close_cb = new Callback(Chromium.class,
				"on_before_close", void.class,
				new Type[] { long.class, long.class });
		lifeSpanHandler.on_before_close = checkGetAddress(
				lifeSpanHandler.on_before_close_cb);
		lifeSpanHandler.do_close_cb = new Callback(Chromium.class, "do_close",
				int.class, new Type[] { long.class, long.class });
		lifeSpanHandler.do_close = checkGetAddress(lifeSpanHandler.do_close_cb);
		lifeSpanHandler.on_after_created_cb = new Callback(Chromium.class,
				"on_after_created", void.class,
				new Type[] { long.class, long.class });
		lifeSpanHandler.on_after_created = checkGetAddress(
				lifeSpanHandler.on_after_created_cb);
		lifeSpanHandler.on_before_popup_cb = new Callback(Chromium.class,
				"on_before_popup", int.class,
				new Type[] { long.class, long.class, long.class, long.class,
						long.class, int.class, int.class, long.class,
						long.class, long.class, long.class, int.class });
		lifeSpanHandler.on_before_popup = checkGetAddress(
				lifeSpanHandler.on_before_popup_cb);

		clientHandler.get_life_span_handler_cb = new Callback(Chromium.class,
				"get_life_span_handler", long.class, new Type[] { long.class });
		clientHandler.get_life_span_handler = checkGetAddress(
				clientHandler.get_life_span_handler_cb);
		lifeSpanHandler.ptr = C.malloc(cef_life_span_handler_t.sizeof);
		ChromiumLib.memmove(lifeSpanHandler.ptr, lifeSpanHandler,
				cef_life_span_handler_t.sizeof);
	}

	private static void set_load_handler() {
		loadHandler = CEFFactory.newLoadHandler();
		loadHandler.on_loading_state_change_cb = new Callback(Chromium.class,
				"on_loading_state_change", void.class, new Type[] { long.class,
						long.class, int.class, int.class, int.class });
		loadHandler.on_loading_state_change = checkGetAddress(
				loadHandler.on_loading_state_change_cb);
		clientHandler.get_load_handler_cb = new Callback(Chromium.class,
				"get_load_handler", long.class, new Type[] { long.class });
		clientHandler.get_load_handler = checkGetAddress(
				clientHandler.get_load_handler_cb);
		loadHandler.ptr = C.malloc(cef_load_handler_t.sizeof);
		ChromiumLib.memmove(loadHandler.ptr, loadHandler,
				cef_load_handler_t.sizeof);
	}

	private static void set_request_handler() {
		requestHandler = CEFFactory.newRequestHandler();
		requestHandler.on_before_browse_cb = new Callback(Chromium.class,
				"on_before_browse", int.class, new Type[] { long.class,
						long.class, long.class, long.class, int.class });
		requestHandler.on_before_browse = checkGetAddress(
				requestHandler.on_before_browse_cb);
		requestHandler.get_auth_credentials_cb = new Callback(Chromium.class,
				"get_auth_credentials", int.class,
				new Type[] { long.class, long.class, long.class, int.class,
						long.class, int.class, long.class, long.class,
						long.class });
		requestHandler.get_auth_credentials = checkGetAddress(
				requestHandler.get_auth_credentials_cb);

		clientHandler.get_request_handler_cb = new Callback(Chromium.class,
				"get_request_handler", long.class, new Type[] { long.class });
		clientHandler.get_request_handler = checkGetAddress(
				clientHandler.get_request_handler_cb);
		requestHandler.ptr = C.malloc(cef_request_handler_t.sizeof);
		ChromiumLib.memmove(requestHandler.ptr, requestHandler,
				cef_request_handler_t.sizeof);
	}

	private static void setCookieVisitor() {
		cookieVisitor = CEFFactory.newCookieVisitor();
		cookieVisitor.visit_cb = new Callback(Chromium.class,
				"cookieVisitor_visit", int.class, new Type[] { long.class,
						long.class, int.class, int.class, int.class });
		cookieVisitor.visit = checkGetAddress(cookieVisitor.visit_cb);

		cookieVisitor.ptr = C.malloc(cef_cookie_visitor_t.sizeof);
		ChromiumLib.memmove(cookieVisitor.ptr, cookieVisitor,
				cef_cookie_visitor_t.sizeof);
	}

	private static void setupCookies() {
		WebBrowser.NativeClearSessions = () -> {
			ChromiumLib.cefswt_delete_cookies();
		};
		WebBrowser.NativeSetCookie = () -> {
			final List<HttpCookie> cookies = HttpCookie
					.parse(WebBrowser.CookieValue);
			for (final HttpCookie cookie : cookies) {
				long age = cookie.getMaxAge();
				if (age != -1) {
					age = Instant.now().plusSeconds(age).getEpochSecond();
				}
				WebBrowser.CookieResult = ChromiumLib.cefswt_set_cookie(
						WebBrowser.CookieUrl, cookie.getName(),
						cookie.getValue(), cookie.getDomain(), cookie.getPath(),
						cookie.getSecure() ? 1 : 0, cookie.isHttpOnly() ? 1 : 0,
						age);
				// debug("CookieSet " + WebBrowser.CookieUrl + " " +
				// cookie.getName() + " " + cookie.getValue() + " " +
				// cookie.getDomain());
				break;
			}
		};
		WebBrowser.NativeGetCookie = () -> {
			if (cookieVisitor == null) {
				setCookieVisitor();
			}
			cookieVisited = new CompletableFuture<>();
			final boolean result = ChromiumLib
					.cefswt_get_cookie(WebBrowser.CookieUrl, cookieVisitor.ptr);
			if (!result) {
				cookieVisited = null;
				throw new SWTException("Failed to get cookies");
			}
			try {
				cookieVisited.get(100, TimeUnit.MILLISECONDS);
			} catch (InterruptedException | ExecutionException
					| TimeoutException e) {
				// no cookies found
			} finally {
				cookieVisited = null;
			}
		};
	}

	private static boolean useSwtDialogs() {
		return "gtk".equals(SWT.getPlatform()) || !"native"
				.equals(System.getProperty("swt.chromium.dialogs", "swt"));
	}

	// static int cbs = 0;
	static long checkGetAddress(final Callback cb) {
		final long address = cb.getAddress();
		// cbs++;
		if (address == 0) {
			throw new SWTError(SWT.ERROR_NO_MORE_CALLBACKS);
		}
		// debug("CALLBACKS "+cbs);
		return address;
	}

	static int cookieVisitor_visit(final long self, final long cefcookie,
			final int count, final int total, final int delete) {
		final String name = ChromiumLib.cefswt_cookie_to_java(cefcookie);
		debug("Visitor " + count + "/" + total + ": " + name + ":"
				+ Thread.currentThread());
		if (WebBrowser.CookieName != null
				&& WebBrowser.CookieName.equals(name)) {
			final String value = ChromiumLib.cefswt_cookie_value(cefcookie);
			debug("cookie value: " + value);
			WebBrowser.CookieValue = value;
			cookieVisited.complete(true);
			return 0;
		}
		return 1;
	}

	static void disposeCallback(final Callback cb) {
		if (cb != null) {
			cb.dispose();
		}
		// cbs--;
	}

	static int do_close(final long plifeSpanHandler, final long browser) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("DoClose: " + id);
		return safeGeInstance(id).do_close(browser);
	}

	static int get_auth_credentials(final long self, final long browser,
			final long frame, final int isProxy, final long host,
			final int port, final long realm, final long scheme,
			final long callback) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		// debug("on_before_browse: " + id);
		return safeGeInstance(id).get_auth_credentials(browser, frame, host,
				port, realm, callback);
	}

	static long get_browser_process_handler(final long app) {
		// debug("GetBrowserProcessHandler");
		if (browserProcessHandler == null) {
			return 0;
		}
		return browserProcessHandler.ptr;
	}

	static long get_context_menu_handler(final long client) {
		// debug("GetContextMenuHandler");
		if (contextMenuHandler == null) {
			return 0;
		}
		return contextMenuHandler.ptr;
	}

	static long get_display_handler(final long client) {
		// debug("GetDisplayHandler");
		if (displayHandler == null) {
			return 0;
		}
		return displayHandler.ptr;
	}

	static long get_focus_handler(final long client) {
		// debug("GetFocusHandler");
		if (focusHandler == null) {
			return 0;
		}
		return focusHandler.ptr;
	}

	static long get_jsdialog_handler(final long client) {
		// debug("GetJSDialogHandler");
		if (jsDialogHandler == null) {
			return 0;
		}
		return jsDialogHandler.ptr;
	}

	static long get_life_span_handler(final long client) {
		// debug("GetLifeSpanHandler");
		if (lifeSpanHandler == null) {
			return 0;
		}
		return lifeSpanHandler.ptr;
	}

	static long get_load_handler(final long client) {
		// debug("GetLoadHandler");
		if (loadHandler == null) {
			return 0;
		}
		return loadHandler.ptr;
	}

	static long get_request_handler(final long client) {
		// debug("GetRequestHandler");
		if (requestHandler == null) {
			return 0;
		}
		return requestHandler.ptr;
	}

	static void on_address_change(final long self, final long browser,
			final long frame, final long url) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("on_address_change: " + id);
		safeGeInstance(id).on_address_change(browser, frame, url);
	}

	static void on_after_created(final long self, final long browser) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("on_after_created: " + id);
		if (browser != 0) {
			browsers.incrementAndGet();
		}

		safeGeInstance(id).on_after_created(browser);
	}

	static int on_before_browse(final long self, final long browser,
			final long frame, final long request, final int is_redirect) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		// debug("on_before_browse: " + id);
		return safeGeInstance(id).on_before_browse(browser, frame, request);
	}

	static void on_before_close(final long plifeSpanHandler,
			final long browser) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("OnBeforeClose" + id);
		instances.remove(id).on_before_close(browser);

		final int decrementAndGet = browsers.decrementAndGet();

		ChromiumLib.cefswt_free(browser);
		if (decrementAndGet == 0) {
			// debug("freelAll now");
			// freeAll(display);
		}
		// not always called on linux
		disposingAny--;
		if (decrementAndGet == 0 && shuttindDown) {
			internalShutdown();
		}
	}

	static int on_before_popup(final long self, final long browser,
			final long frame, final long target_url,
			final long target_frame_name, final int target_disposition,
			final int user_gesture, final long popupFeaturesPtr,
			final long windowInfo, final long client, final long settings,
			final int no_javascript_access) {
		loopDisable = true;
		pumpDisable = true;
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("on_before_popup: " + id);
		final int ret = safeGeInstance(id).on_before_popup(browser,
				popupFeaturesPtr, windowInfo, client);
		loopDisable = false;
		return ret;
	}

	static int on_before_unload_dialog(final long self_, final long browser,
			final long msg, final int is_reload, final long callback) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		return safeGeInstance(id).on_before_unload_dialog(browser, msg,
				is_reload, callback);
	}

	static void on_dialog_closed(final long self_, final long browser) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		safeGeInstance(id).on_dialog_closed(browser);
	}

	static void on_got_focus(final long focusHandler, final long browser) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("on_got_focus: " + id);
		safeGeInstance(id).on_got_focus(browser);
	}

	static int on_jsdialog(final long self_, final long browser,
			final long origin_url, final int dialog_type,
			final long message_text, final long default_prompt_text,
			final long callback, final int suppress_message) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("on_jsdialog: " + id);
		return safeGeInstance(id).on_jsdialog(browser, origin_url, dialog_type,
				message_text, default_prompt_text, callback);
	}

	static void on_loading_state_change(final long self_, final long browser,
			final int isLoading, final int canGoBack, final int canGoForward) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("on_loading_state_change: " + id);
		safeGeInstance(id).on_loading_state_change(browser, isLoading,
				canGoBack, canGoForward);
	}

	static int on_process_message_received(final long client,
			final long browser, final int source, final long processMessage) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("on_process_message_received: " + id);
		return safeGeInstance(id).on_process_message_received(browser, source,
				processMessage);
	}

	static void on_schedule_message_pump_work(final long pbrowserProcessHandler,
			final int delay, final int _delay2) {
		if (browsers.get() <= 0 || pumpDisable || disposingAny > 0) {
			return;
		}
		final Display display = Display.getDefault();
		// debug("pump "+delay);
		final Runnable scheduleWork = () -> {
			restartLoop(display, delay);
			display.timerExec(-1, loopWorkRunnable);
			// debug("WORK PUMP DELAYED");
			display.timerExec(delay, loopWorkRunnable);
		};
		if (Display.getCurrent() != null) {
			if (delay <= 0) {
				restartLoop(display, 0);
				// debug("WORK PUMP NOW");
				display.asyncExec(loopWorkRunnable);
			} else {
				scheduleWork.run();
			}
		} else {
			if (delay <= 0) {
				display.asyncExec(() -> {
					restartLoop(display, 0);
					// debug("WORK PUMP ALMOST NOW");
					loopWorkRunnable.run();
				});
			} else {
				display.asyncExec(scheduleWork);
			}
		}
	}

	static int on_set_focus(final long focusHandler, final long browser,
			final int focusSource) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("on_set_focus: " + id);
		return safeGeInstance(id).on_set_focus(browser);
	}

	static void on_status_message(final long self, final long browser,
			final long status) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		// debug("on_status_message: " + id);
		safeGeInstance(id).on_status_message(browser, status);
	}

	static void on_take_focus(final long focusHandler, final long browser,
			final int next) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("on_take_focus: " + id);
		safeGeInstance(id).on_take_focus(browser, next);
	}

	static void on_title_change(final long self, final long browser,
			final long title) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("on_title_change: " + id);
		safeGeInstance(id).on_title_change(browser, title);
	}

	static int popup_do_close(final long plifeSpanHandler, final long browser) {
		debug("popup DoClose");
		disposingAny++;
		return 0;
	}

	static long popup_get_life_span_handler(final long client) {
		if (popupLifeSpanHandler == null) {
			return 0;
		}
		return popupLifeSpanHandler.ptr;
	}

	static void popup_on_after_created(final long plifeSpanHandler,
			final long browser) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("popup on_after_created: " + id);
		try {
			// not sleeping here causes deadlock with multiple window.open
			Thread.sleep(LOOP);
		} catch (final InterruptedException e) {
		}
	}

	static void popup_on_before_close(final long plifeSpanHandler,
			final long browser) {
		debug("popup OnBeforeClose");
		popupHandlers--;
		if (popupHandlers == 0) {
			disposeCallback(popupLifeSpanHandler.on_after_created_cb);
			disposeCallback(popupLifeSpanHandler.do_close_cb);
			disposeCallback(popupLifeSpanHandler.on_before_close_cb);
			C.free(popupLifeSpanHandler.ptr);
			popupLifeSpanHandler = null;
		}
		disposingAny--;
	}

	static int run_context_menu(final long self, final long browser,
			final long frame, final long params, final long model,
			final long callback) {
		final int id = ChromiumLib.cefswt_get_id(browser);
		debug("run_context_menu: " + id);
		return safeGeInstance(id).run_context_menu(browser, callback);
	}

	private long browser;

	private boolean canGoBack;

	private boolean canGoForward;

	private final CompletableFuture<Boolean> created = new CompletableFuture<>();

	private Dispose disposing = Dispose.No;

	private CompletableFuture<Boolean> enableProgress = new CompletableFuture<>();

	private FocusListener focusListener;

	private boolean hasFocus;

	private String[] headers;

	private long hwnd;

	private boolean ignoreFirstFocus = true;

	private int instance;

	private WindowEvent isPopup;

	private PaintListener paintListener;

	private String postData;

	private String text = "";

	private CompletableFuture<String> textReady;

	private cef_string_visitor_t textVisitor;

	private String url;

	Browser chromium;

	public Chromium() {
	}

	@Override
	public boolean back() {
		if (lib == null) {
			SWT.error(SWT.ERROR_FAILED_LOAD_LIBRARY);
		}
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
		debugPrint("call try_close_browser");
		disposing = Dispose.FromClose;
		ChromiumLib.cefswt_close_browser(browser, 0);

		final long t = System.currentTimeMillis();
		long end = t + 10000;
		final Shell shell = chromium.getShell();
		final Display display = shell.getDisplay();
		while (!shell.isDisposed() && System.currentTimeMillis() < end) {
			// debug("in close, disposing:"+disposing);
			if (disposing == Dispose.Unload) {
				// debug("in close, disposing:"+disposing);
				end += 1000;
			} else if (disposing == Dispose.UnloadClosed) {
				debug("in close, disposing:" + disposing);
				disposing = Dispose.WaitIfClosed;
				end = System.currentTimeMillis() + LOOP * 2;
			} else if (disposing == Dispose.DoIt) {
				debug("in close, disposing:" + disposing);
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
		// debugPrint("try_close_browser returned");
		return closed;
	}

	@Override
	public void create(final Composite parent, final int style) {
		initCEF(chromium.getDisplay());
		// debugPrint("initCef Done");
		chromium.setBackground(
				parent.getDisplay().getSystemColor(SWT.COLOR_TRANSPARENT));
		paintListener = new PaintListener() {
			@Override
			public void paintControl(final PaintEvent e) {
				debugPrint("paintControl");
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

			if (!ChromiumLib.cefswt_function(browser, function.name,
					function.index)) {
				throw new SWTException("Cannot create BrowserFunction");
			}
		});
	}

	@Override
	public void destroyFunction(final BrowserFunction function) {
		checkBrowser();
		deregisterFunction(function);
	}

	public void dispose() {
		debugPrint("in dispose, disposing " + disposing);
		if (disposing == Dispose.FromDispose || isDisposed()) {
			return;
		}
		final boolean callClose = disposing == Dispose.No;
		disposing = Dispose.FromDispose;
		disposingAny++;
		if (focusListener != null) {
			chromium.removeFocusListener(focusListener);
		}
		focusListener = null;
		if (browser != 0 && callClose) {
			// browsers.decrementAndGet();
			debugPrint("call close_browser");
			ChromiumLib.cefswt_close_browser(browser, 1);
			waitForClose(Display.getDefault());
		}
	}

	@Override
	public Object evaluate(final String script) throws SWTException {
		if (lib == null) {
			SWT.error(SWT.ERROR_FAILED_LOAD_LIBRARY);
		}
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
				// debugPrint("eval retured: " +type + ":"+valuePtr);
				if (!(loopDisable && ("cocoa".equals(SWT.getPlatform())
						|| "gtk".equals(SWT.getPlatform())))) {
					chromium.getDisplay().readAndDispatch();
				}
				if (!loopDisable) {
					// lib.cefswt_do_message_loop_work();
				}
			} else {
				final String value = ChromiumLib
						.cefswt_cstring_to_java(valuePtr);
				debugPrint("eval returned: " + type + ":" + value);
				ret[0] = mapType(type, value);
			}
		};
		final Callback callback_cb = new Callback(callback, "invoke",
				void.class, new Type[] { int.class, int.class, long.class });

		final StringBuilder buffer = new StringBuilder("(function() {");
		buffer.append("\n");
		buffer.append(script);
		buffer.append("\n})()");

		final boolean returnSt = ChromiumLib.cefswt_eval(browser,
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
			ChromiumLib.cefswt_execute(browser, script);
		});
		return true;
	}

	@Override
	public boolean forward() {
		if (lib == null) {
			SWT.error(SWT.ERROR_FAILED_LOAD_LIBRARY);
		}
		if (canGoForward) {
			ChromiumLib.cefswt_go_forward(browser);
			return true;
		}
		return false;
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
		if (lib == null) {
			SWT.error(SWT.ERROR_FAILED_LOAD_LIBRARY);
		}
		if (browser == 0) {
			if (this.url == null) {
				return "about:blank";
			}
			return getPlainUrl(this.url);
		}
		final long urlPtr = ChromiumLib.cefswt_get_url(browser);
		String cefurl = null;
		if (urlPtr != 0) {
			cefurl = ChromiumLib.cefswt_cstring_to_java(urlPtr);
		}
		// debugPrint("getUrl1:" + cefurl);
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

	@Override
	public void refresh() {
		if (lib == null) {
			SWT.error(SWT.ERROR_FAILED_LOAD_LIBRARY);
		}
		jsEnabled = jsEnabledOnNextPage;
		if (browser != 0) {
			ChromiumLib.cefswt_reload(browser);
		}
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
				final boolean s = setUrl(tmp.toUri().toString(), null, null);
				return s;
			} catch (final IOException e) {
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
			debugPrint("set url: " + url);
			doSetUrl(url, postData, headers);
		}
		return true;
	}

	@Override
	public void stop() {
		if (lib == null) {
			SWT.error(SWT.ERROR_FAILED_LOAD_LIBRARY);
		}
		if (browser != 0) {
			ChromiumLib.cefswt_stop(browser);
		}
	}

	private int cefColor(final int a, final int r, final int g, final int b) {
		return a << 24 | r << 16 | g << 8 | b << 0;
	}

	private synchronized void checkBrowser() {
		if (lib == null) {
			SWT.error(SWT.ERROR_FAILED_LOAD_LIBRARY);
		}
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

		instance = ++INSTANCES;
		debug("Registering chromium instance " + instance);
		instances.put(instance, this);
		ChromiumLib.cefswt_create_browser(hwnd, url, clientHandler.ptr, size.x,
				size.y, jsEnabledOnNextPage ? 1 : 0, cefBgColor);
	}

	private void createDefaultPopup(final long windowInfo, final long client,
			final WindowEvent event) {
		debugPrint("default popup");
		instance = ++INSTANCES;
		debug("Registering chromium default popup " + instance);

		if (popupHandlers == 0) {
			popupLifeSpanHandler = CEFFactory.newLifeSpanHandler();
			popupLifeSpanHandler.on_after_created_cb = new Callback(
					Chromium.class, "popup_on_after_created", void.class,
					new Type[] { long.class, long.class });
			popupLifeSpanHandler.on_after_created = checkGetAddress(
					popupLifeSpanHandler.on_after_created_cb);
			popupLifeSpanHandler.on_before_close_cb = new Callback(
					Chromium.class, "popup_on_before_close", void.class,
					new Type[] { long.class, long.class });
			popupLifeSpanHandler.on_before_close = checkGetAddress(
					popupLifeSpanHandler.on_before_close_cb);
			popupLifeSpanHandler.do_close_cb = new Callback(Chromium.class,
					"popup_do_close", int.class,
					new Type[] { long.class, long.class });
			popupLifeSpanHandler.do_close = checkGetAddress(
					popupLifeSpanHandler.do_close_cb);

			popupLifeSpanHandler.ptr = C.malloc(cef_life_span_handler_t.sizeof);
			ChromiumLib.memmove(popupLifeSpanHandler.ptr, popupLifeSpanHandler,
					cef_life_span_handler_t.sizeof);

			if (popupClientHandler == null) {
				popupClientHandler = CEFFactory.newClient();
				popupClientHandler.get_life_span_handler_cb = new Callback(
						Chromium.class, "popup_get_life_span_handler",
						long.class, new Type[] { long.class });
				popupClientHandler.get_life_span_handler = checkGetAddress(
						popupClientHandler.get_life_span_handler_cb);

				popupClientHandler.ptr = C.malloc(cef_client_t.sizeof);
				ChromiumLib.memmove(popupClientHandler.ptr, popupClientHandler,
						cef_client_t.sizeof);
			}
		}
		popupHandlers++;

		ChromiumLib.cefswt_set_window_info_parent(windowInfo, client,
				popupClientHandler.ptr, 0,
				event.location != null ? event.location.x : 0,
				event.location != null ? event.location.y : 0,
				event.size != null ? event.size.x : 0,
				event.size != null ? event.size.y : 0);
	}

	private void createPopup(final long windowInfo, final long client,
			final WindowEvent event) {
		if (paintListener != null) {
			chromium.removePaintListener(paintListener);
			paintListener = null;
		} else {
			// TODO: destroy browser first?
			// instances.put(instance, this);
			debug("Unregistering chromium phantom popup " + instance);
			instances.remove(instance);
		}
		instance = ++INSTANCES;
		debug("Registering chromium popup " + instance);
		instances.put(instance, this);
		isPopup = event;

		final String platform = SWT.getPlatform();
		if ("gtk".equals(platform) && chromium.getDisplay()
				.getActiveShell() != chromium.getShell()) {
			// on linux the window hosting the popup needs to be created
			final boolean visible = chromium.getShell().isVisible();
			chromium.getShell().open();
			chromium.getShell().setVisible(visible);
		}

		prepareBrowser();
		final long popupHandle = hwnd;
		debugPrint("popup will use hwnd:" + popupHandle);
		Point size = new Point(0, 0);
		if ("gtk".equals(SWT.getPlatform())) {
			size = chromium.getParent().getSize();
			size = DPIUtil.autoScaleUp(size);
		}

		ChromiumLib.cefswt_set_window_info_parent(windowInfo, client,
				clientHandler.ptr, popupHandle, 0, 0, size.x, size.y);
		debugPrint("reparent popup");
	}

	private void debugPrint(final String log) {
		if (debug) {
			System.out.println("J" + instance + ":"
					+ Thread.currentThread().getName() + ":" + log
					+ (this.url != null ? " (" + getPlainUrl(this.url) + ")"
							: " empty-url"));
		}
	}

	private int do_close(final long browser) {
		if (!ChromiumLib.cefswt_is_same(Chromium.this.browser, browser)) {
			debugPrint(
					"DoClose popup:" + Chromium.this.browser + ":" + browser);
			return 0;
		}
		final Display display = Display.getDefault();
		if (/* !disposing && */ !isDisposed() && closeWindowListeners != null) {
			final org.eclipse.swt.browser.WindowEvent event = new org.eclipse.swt.browser.WindowEvent(
					chromium);
			event.display = display;
			event.widget = chromium;
			// event.browser = chromium;
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
				if (!"gtk".equals(SWT.getPlatform())) {
					chromium.dispose();
				}
			}
		}
		// if ("gtk".equals(SWT.getPlatform())) {
		// waitForClose(display);
		// }
		// do not send close notification to top level window
		// returning 0, cause the window to close
		debugPrint("AFTER DoClose");

		return disposing == Dispose.FromBrowser
				&& "gtk".equals(SWT.getPlatform()) ? 0 : 1;
	}

	private CompletableFuture<Void> doSetUrl(final String url,
			final String postData, final String[] headers) {
		return enableProgress.thenRun(() -> {
			debugPrint("load url");
			doSetUrlPost(browser, url, postData, headers);
		});
	}

	private void freeTextVisitor() {
		debugPrint("free text visitor");
		disposeCallback(textVisitor.visit_cb);
		freeDelayed(textVisitor.ptr);
		textVisitor = null;
	}

	private int get_auth_credentials(final long browser2, final long frame,
			final long host, final int port, final long realm,
			final long callback) {
		if (isDisposed()) {
			return 0;
		}

		final AuthenticationEvent event = new AuthenticationEvent(chromium);
		event.display = chromium.getDisplay();
		event.widget = chromium;
		event.doit = true;
		String protocol = "http";
		try {
			final URL u = new URL(this.url);
			protocol = u.getProtocol();
		} catch (final MalformedURLException e) {
		}
		final String hostStr = host != 0
				? ChromiumLib.cefswt_cefstring_to_java(host)
				: "";
		final String realmStr = realm != 0
				? ChromiumLib.cefswt_cefstring_to_java(realm)
				: null;
		event.location = protocol + "://" + hostStr;
		debugPrint("get_auth_credentials:" + event.location);
		chromium.getDisplay().syncExec(() -> {
			for (final AuthenticationListener listener : authenticationListeners) {
				listener.authenticate(event);
			}
			if (event.doit == true && event.user == null
					&& event.password == null) {
				new AuthDialog(chromium.getShell()).open(event, realmStr);
			}
		});
		ChromiumLib.cefswt_auth_callback(callback, event.user, event.password,
				event.doit ? 1 : 0);
		return event.doit ? 1 : 0;
	}

	private Point getChromiumSize() {
		final Point size = chromium.getSize();
		if ("cocoa".equals(SWT.getPlatform())) {
			return size;
		}
		return DPIUtil.autoScaleUp(size);
	}

	private void initCEF(final Display display) {
		synchronized (lib) {
			if (app == null) {
				// CEFFactory.create();
				app = CEFFactory.newApp();
				// cefInitilized = new CompletableFuture<>();
				browserProcessHandler = CEFFactory.newBrowserProcessHandler();
				// browserProcessHandler.on_context_initialized_cb = new
				// Callback(Chromium.class, "on_context_initialized",
				// void.class, new Type[] {long.class});
				// browserProcessHandler.on_context_initialized =
				// browserProcessHandler.on_context_initialized_cb.getAddress();
				browserProcessHandler.on_schedule_message_pump_work_cb = new Callback(
						Chromium.class, "on_schedule_message_pump_work",
						void.class,
						new Type[] { long.class, int.class, int.class });
				browserProcessHandler.on_schedule_message_pump_work = checkGetAddress(
						browserProcessHandler.on_schedule_message_pump_work_cb);
				//
				app.get_browser_process_handler_cb = new Callback(
						Chromium.class, "get_browser_process_handler",
						long.class, new Type[] { long.class });
				app.get_browser_process_handler = checkGetAddress(
						app.get_browser_process_handler_cb);

				browserProcessHandler.ptr = C
						.malloc(cef_browser_process_handler_t.sizeof);
				ChromiumLib.memmove(browserProcessHandler.ptr,
						browserProcessHandler,
						cef_browser_process_handler_t.sizeof);

				int debugPort = 0;
				try {
					debugPort = Integer.parseInt(System.getProperty(
							"org.eclipse.swt.chromium.remote-debugging-port",
							"0"));
				} catch (final NumberFormatException e) {
					debugPort = 0;
				}
				app.ptr = C.malloc(cef_app_t.sizeof);
				ChromiumLib.memmove(app.ptr, app, cef_app_t.sizeof);

				final boolean suspend = "win32".equals(SWT.getPlatform())
						? false
						: Boolean.getBoolean("swt.chromium.suspendThreads");
				Thread main = null;
				Thread[] threads = null;
				int threadsCount = 0;
				if (suspend) {
					main = Thread.currentThread();
					threads = new Thread[Thread.activeCount() * 2];
					threadsCount = Thread.enumerate(threads);
					for (int i = 0; i < threadsCount; i++) {
						final Thread t = threads[i];
						if (t != main) {
							t.suspend();
						}
					}
				}

				ChromiumLib.cefswt_init(app.ptr, cefrustPath, cefPath, VERSION,
						debugPort);

				if (suspend) {
					for (int i = 0; i < threadsCount; i++) {
						final Thread t = threads[i];
						if (t != main) {
							t.resume();
						}
					}
				}

				display.disposeExec(() -> {
					if (app == null || shuttindDown) {
						// already shutdown
						return;
					}
					internalShutdown();
				});
			}
		}
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
			return Double.parseDouble(value);
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

	private void on_address_change(final long browser, final long frame,
			final long url) {
		// debugPrint("on_address_change");
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
			debugPrint("!on_address_change to " + event.location + " "
					+ (event.top ? "main" : "!main"));
			return;
		}
		// if (!("about:blank".equals(event.location) && ignoreFirstEvents)) {
		debugPrint("on_address_change to " + event.location + " "
				+ (event.top ? "main" : "!main"));
		chromium.getDisplay().asyncExec(() -> {
			for (final LocationListener listener : locationListeners) {
				listener.changed(event);
			}
		});
	}

	private void on_after_created(final long browser) {
		if (isDisposed() || visibilityWindowListeners == null) {
			return;
		}
		debugPrint("on_after_created: " + browser);
		if (browser != 0) {
			Chromium.this.browser = browser;
			if (this.isPopup == null) {
				final org.eclipse.swt.graphics.Point size = getChromiumSize();
				ChromiumLib.cefswt_resized(browser, size.x, size.y);
			}
			if (this.isPopup != null && this.url != null) {
				debugPrint("load url after created");
				doSetUrlPost(browser, url, postData, headers);
			} else if (!"about:blank".equals(this.url)) {
				enableProgress.complete(true);
			}
		}
		created.complete(true);

		if (browsers.get() == 1) {
			debugPrint("STARTING MSG LOOP");
			final Display display = chromium.getDisplay();
			doMessageLoop(display);
		}

		// chromium.getDisplay().asyncExec(() -> {
		debugPrint("on_after_created handling " + browser);
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
				chromium.getShell().setSize(
						chromium.getShell().computeSize(size.x, size.y));
			}

			// chromium.getDisplay().asyncExec(() -> {
			for (final VisibilityWindowListener listener : visibilityWindowListeners) {
				listener.show(event);
			}
			// });
		}
		// });
		try {
			// not sleeping here causes deadlock with multiple window.open
			Thread.sleep(LOOP);
		} catch (final InterruptedException e) {
		}
	}

	private int on_before_browse(final long browser2, final long frame,
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
			debugPrint("on_before_browse:" + event.location);
			try {
				loopDisable = true;
				for (final LocationListener listener : locationListeners) {
					listener.changing(event);
				}
			} finally {
				loopDisable = false;
			}
			if (!event.doit) {
				debugPrint("canceled nav, dependats:"
						+ enableProgress.getNumberOfDependents());
				enableProgress = new CompletableFuture<>();
			}
			return event.doit ? 0 : 1;
		}
		return 0;
	}

	private void on_before_close(final long browser) {
		if (disposing == Dispose.FromBrowser
				&& "gtk".equals(SWT.getPlatform())) {
			chromium.dispose();
		}
		this.browser = 0;
		this.chromium = null;
		debugPrint("closed");
		if (textVisitor != null) {
			// debugPrint("text visitor still pending");
			Display.getCurrent().asyncExec(() -> {
				if (textVisitor != null) {
					freeTextVisitor();
				}
			});
		}
	}

	private int on_before_popup(final long browser, final long popupFeaturesPtr,
			final long windowInfo, final long client) {
		if (isDisposed()) {
			return 1;
		}
		if (openWindowListeners == null) {
			return 0;
		}

		final WindowEvent event = new WindowEvent(chromium);

		final cef_popup_features_t popupFeatures = new cef_popup_features_t();
		ChromiumLib.memmove(popupFeatures, popupFeaturesPtr,
				ChromiumLib.cef_popup_features_t_sizeof());

		try {
			// not sleeping here causes deadlock with multiple window.open
			Thread.sleep(LOOP);
		} catch (final InterruptedException e) {
		}
		chromium.getDisplay().syncExec(() -> {
			debugPrint("on_before_popup syncExec" + browser);
			event.display = chromium.getDisplay();
			event.widget = chromium;
			event.required = false;
			event.addressBar = popupFeatures.locationBarVisible == 1;
			event.menuBar = popupFeatures.menuBarVisible == 1;
			event.statusBar = popupFeatures.statusBarVisible == 1;
			event.toolBar = popupFeatures.toolBarVisible == 1;
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
				createDefaultPopup(windowInfo, client, event);
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

	private int on_before_unload_dialog(final long browser,
			final long message_text, final int is_reload, final long callback) {
		debug("on_before_unload_dialog disposing: " + disposing);
		if (disposing == Dispose.FromClose) {
			disposing = Dispose.Unload;

			if (useSwtDialogs()) {
				final String msg = ChromiumLib
						.cefswt_cefstring_to_java(message_text);
				// Display.getDefault().asyncExec(() -> {
				// ChromiumLib.cefswt_dialog_close(callback, 0, 0);
				openJsDialog(cef_jsdialog_handler_t.JSDIALOGTYPE_PROMPT,
						"Are you sure you want to leave this page?", msg, 0,
						callback);
				disposing = Dispose.UnloadClosed;
				// });
				return 1;
			}
		}
		return 0;
	}

	private void on_dialog_closed(final long browser) {
		debug("on_dialog_closed_cb disposing: " + disposing);
		if (disposing == Dispose.Unload) {
			disposing = Dispose.UnloadClosed;
		}
	}

	private void on_got_focus(final long browser2) {
		if (!isDisposed()) {
			hasFocus = true;
			if (!isDisposed()
					&& chromium.getDisplay().getFocusControl() != null) {
				chromium.setFocus();
			}
			browserFocus(true);
		}
	}

	private int on_jsdialog(final long browser, final long origin_url,
			final int dialog_type, final long message_text,
			final long default_prompt_text, final long callback) {
		if (isDisposed()) {
			return 0;
		}

		// String prompt =
		// ChromiumLib.cefswt_cefstring_to_java(default_prompt_text);
		final String url = ChromiumLib.cefswt_cefstring_to_java(origin_url);
		final String title = getPlainUrl(url);
		final String msg = ChromiumLib.cefswt_cefstring_to_java(message_text);
		openJsDialog(dialog_type, title, msg, default_prompt_text, callback);
		return 1;
	}

	private void on_loading_state_change(final long browser,
			final int isLoading, final int canGoBack, final int canGoForward) {
		Chromium.this.canGoBack = canGoBack == 1;
		Chromium.this.canGoForward = canGoForward == 1;
		if (isDisposed() || progressListeners == null) {
			return;
		}
		if (isLoading == 0) {
			for (final BrowserFunction function : functions.values()) {
				if (function.index != 0) {
					if (!ChromiumLib.cefswt_function(browser, function.name,
							function.index)) {
						throw new SWTException("Cannot create BrowserFunction");
					}
				}
			}
		}
		updateText();
		if (isPopup != null) {
			textReady.thenRun(() -> enableProgress.complete(true));
		} else if (!enableProgress.isDone() && isLoading == 0) {
			textReady.thenRun(() -> {
				enableProgress.complete(true);
			});
			return;
		} else if (!enableProgress.isDone()) {
			return;
		}
		final ProgressEvent event = new ProgressEvent(chromium);
		event.display = chromium.getDisplay();
		event.widget = chromium;
		event.current = MAX_PROGRESS;
		event.current = isLoading == 1 ? 1 : MAX_PROGRESS;
		event.total = MAX_PROGRESS;
		if (isLoading == 1) {
			debugPrint("progress changed");
			for (final ProgressListener listener : progressListeners) {
				listener.changed(event);
			}
		} else {
			textReady.thenRun(() -> {
				debugPrint("progress completed");
				chromium.getDisplay().asyncExec(() -> {
					for (final ProgressListener listener : progressListeners) {
						listener.completed(event);
					}
				});
			});
		}
	}

	private int on_process_message_received(final long browser,
			final int source, final long processMessage) {
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
		final Object ret = functions.get(id).function(args);

		final Object[] returnPair = convertType(ret);
		final ReturnType returnType = (ReturnType) returnPair[0];
		final String returnStr = (String) returnPair[1];
		ChromiumLib.cefswt_function_return(browser, id, fn.port,
				returnType.intValue(), returnStr);

		return 1;
	}

	private int on_set_focus(final long browser) {
		if (ignoreFirstFocus) {
			ignoreFirstFocus = false;
			return 1;
		}
		return 0;
	}

	private void on_status_message(final long browser, final long status) {
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

	private void on_take_focus(final long browser, final int next) {
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

	private void on_title_change(final long browser, final long title) {
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

	private void openJsDialog(final int dialog_type, final String title,
			final String msg, final long default_prompt_text,
			final long callback) {
		int style = SWT.ICON_WORKING;
		switch (dialog_type) {
		case cef_jsdialog_handler_t.JSDIALOGTYPE_ALERT:
			style = SWT.ICON_INFORMATION;
			break;
		case cef_jsdialog_handler_t.JSDIALOGTYPE_CONFIRM:
			style = SWT.ICON_WARNING;
			break;
		case cef_jsdialog_handler_t.JSDIALOGTYPE_PROMPT:
			style = SWT.ICON_QUESTION | SWT.YES | SWT.NO;
			break;
		}
		final Consumer<Integer> close = open -> {
			final int r = open == SWT.OK || open == SWT.YES ? 1 : 0;
			debug("JS Dialog Closed with " + r);
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

		chromium.addDisposeListener(e -> {
			debugPrint("disposing chromium");
			dispose();
		});
		focusListener = new CefFocusListener();
		chromium.addFocusListener(focusListener);

		// set_text_visitor();
		// if (browsers.get() == 0) {
		if (INSTANCES == 0) {
			set_client_handler();
		}

		chromium.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(final ControlEvent e) {
				if (!isDisposed() && browser != 0) {
					final Point size = getChromiumSize();
					ChromiumLib.cefswt_resized(browser, size.x, size.y);
				}
			}
		});
	}

	private int run_context_menu(final long browser2, final long callback) {
		if (chromium.getMenu() != null) {
			chromium.getMenu().setVisible(true);
			ChromiumLib.cefswt_context_menu_cancel(callback);
			return 1;
		}
		return 0;
	}

	private void set_text_visitor() {
		textVisitor = CEFFactory.newStringVisitor();
		textVisitor.visit_cb = new Callback(this, "textVisitor_visit",
				void.class, new Type[] { long.class, long.class });
		textVisitor.visit = checkGetAddress(textVisitor.visit_cb);
		textVisitor.ptr = C.malloc(cef_string_visitor_t.sizeof);
		textVisitor.refs = 1;
		ChromiumLib.memmove(textVisitor.ptr, textVisitor,
				cef_string_visitor_t.sizeof);
	}

	private void updateText() {
		if (browser != 0 && !isDisposed() && disposing == Dispose.No) {
			debugPrint("update text");
			if (textVisitor != null) {
				textVisitor.refs++;
			} else {
				set_text_visitor();
			}
			textReady = new CompletableFuture<>();
			ChromiumLib.cefswt_get_text(browser, textVisitor.ptr);
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
		// debugPrint("cef focus: " + set);
		if (!isDisposed() && browser != 0) {
			final long parent = Display.getDefault().getActiveShell() == null
					? 0
					: getHandle(chromium.getParent());
			if (chromium.getDisplay().getActiveShell() != chromium.getShell()) {
				// System.err.println("Ignore do_message_loop_work due inactive
				// shell");
				return;
			}
			ChromiumLib.cefswt_set_focus(browser, set, parent);
		}
	}

	protected long getHandle(final Composite control) {
		return control.handle;
	}

	boolean isDisposed() {
		return chromium == null || chromium.isDisposed();
	}

	void textVisitor_visit(final long self, final long cefString) {
		// debugPrint("text visited");

		if (--textVisitor.refs == 0) {
			freeTextVisitor();
		}

		final String newtext = cefString != 0
				? ChromiumLib.cefswt_cefstring_to_java(cefString)
				: null;
		if (newtext != null) {
			text = newtext;
			debugPrint("text visited completed");
			textReady.complete(text);
		} else {
			debugPrint("text visited null");
		}
	}

}
