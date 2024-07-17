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
package org.eclipse.set.browser.lib;

import org.eclipse.swt.internal.C;

@SuppressWarnings("javadoc")
public class ChromiumLib extends C {
	// Dialog types
	public static final int JSDIALOGTYPE_ALERT = 0;
	public static final int JSDIALOGTYPE_CONFIRM = 1;
	public static final int JSDIALOGTYPE_PROMPT = 2;

	/**
	 * Native handlers.
	 * 
	 * The following functions create their corresponding CEF object and set up
	 * `handler` to handle callbacks via JNI.
	 * 
	 * For each CEF callback in the native CEF object, the JNI code looks up
	 * whether a method of the same name and a matching signature exists in the
	 * class of the handler object. If so, the handler is set up to call the
	 * Java method.
	 * 
	 * Returns a raw pointer to a native object to be passed to other methods
	 * 
	 * Note that the returned pointer must be free'd manually with
	 * deallocate_cef_*_t
	 */

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_app_t(final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_browser_process_handler_t(
			final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_client_t(final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_context_menu_handler_t(
			final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_cookie_visitor_t(
			final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_display_handler_t(
			final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_download_handler_t(
			final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_focus_handler_t(
			final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_jsdialog_handler_t(
			final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_life_span_handler_t(
			final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_load_handler_t(
			final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_request_handler_t(
			final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_resource_handler_t(
			final Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_scheme_handler_factory_t(
			Object handler);

	/**
	 * @param handler
	 *            the handler for callbacks
	 * 
	 * @return a raw pointer to the native object to be passed to other methods
	 */
	public static final native long allocate_cef_string_visitor_t(
			Object handler);

	/**
	 * @param callback
	 *            cast=(void *)
	 */
	public static final native void cefswt_auth_callback(long callback,
			String user, String password, int cont);

	/** @method flags=no_gen */
	public static final native String cefswt_cefstring_to_java(long string);

	/**
	 * @param callback
	 *            cast=(void *)
	 */
	public static final native void cefswt_context_menu_cancel(long callback);

	/**
	 * @param hwnd
	 *            cast=(void *)
	 * @param clientHandler
	 *            cast=(void *)
	 */
	public static final native long cefswt_create_browser(long hwnd, String url,
			long clientHandler, int w, int h, int js, int cefBgColor);

	/** @method flags=no_gen */
	public static final native String cefswt_cstring_to_java(long string);

	/**
	 * @param callback
	 *            cast=(void *)
	 * @param default_prompt_text
	 *            cast=(void *)
	 */
	public static final native void cefswt_dialog_close(long callback, int i,
			long default_prompt_text);

	public static final native int cefswt_do_message_loop_work();

	/**
	 * @param msg
	 *            cast=(void *)
	 * @param callback
	 *            cast=(void *)
	 */
	public static final native void cefswt_function_arg(long msg, int index,
			long callback);

	/**
	 * @param msg
	 *            cast=(void *)
	 * @param ret
	 *            flags=no_in
	 */
	public static final native void cefswt_function_id(long msg,
			FunctionSt ret);

	/**
	 * @param browser
	 *            cast=(void *)
	 */
	public static final native boolean cefswt_function_return(long browser,
			int id, int port, int returnType, String ret);

	/**
	 * @param browser
	 *            cast=(void *)
	 */
	public static final native void cefswt_go_back(long browser);

	/**
	 * @param browser
	 *            cast=(void *)
	 */
	public static final native void cefswt_go_forward(long browser);

	/**
	 * @param app
	 *            cast=(void *)
	 * @param logLevel
	 * @param logPath
	 */
	public static final native void cefswt_init(long app, String subprocessPath,
			String cefPath, String tempPath, String userAgentProduct,
			String locale, int debugPort, String logPath, int logLevel);

	/**
	 * @param frame
	 *            cast=(void *)
	 */
	public static final native boolean cefswt_is_main_frame(long frame);

	/**
	 * @param browser
	 *            cast=(void *)
	 * @param that
	 *            cast=(void *)
	 */
	public static final native boolean cefswt_is_same(long browser, long that);

	public static final native void cefswt_register_http_host(String name,
			long factory);

	/** @method flags=no_gen */
	public static final native String cefswt_request_to_java(long request);

	public static final native void cefswt_set_intptr(long handle_request_ptr,
			int value);

	/**
	 * @param windowInfo
	 *            cast=(void *)
	 * @param client
	 *            cast=(void *)
	 * @param clientHandler
	 *            cast=(void *)
	 * @param handle
	 *            cast=(void *)
	 */
	public static final native void cefswt_set_window_info_parent(
			long windowInfo, long client, long clientHandler, long handle,
			int x, int y, int w, int h);

	public static final native void cefswt_shutdown();

	/**
	 * @param browser
	 *            cast=(void *)
	 */

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by allocate_cef_app_t
	 */
	public static final native void deallocate_cef_app_t(final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by
	 *            allocate_cef_browser_process_handler_t
	 */
	public static final native void deallocate_cef_browser_process_handler_t(
			final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by allocate_cef_client_t
	 */
	public static final native void deallocate_cef_client_t(final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by
	 *            allocate_cef_context_menu_handler_t
	 */
	public static final native long deallocate_cef_context_menu_handler_t(
			final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by allocate_cef_cookie_visitor_t
	 */
	public static final native long deallocate_cef_cookie_visitor_t(
			final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by allocate_cef_display_handler_t
	 */
	public static final native long deallocate_cef_display_handler_t(
			final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by
	 *            allocate_cef_download_handler_t
	 */
	public static final native long deallocate_cef_download_handler_t(
			final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by allocate_cef_focus_handler_t
	 */
	public static final native long deallocate_cef_focus_handler_t(
			final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by
	 *            allocate_cef_jsdialog_handler_t
	 */
	public static final native long deallocate_cef_jsdialog_handler_t(
			final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by
	 *            allocate_cef_life_span_handler_t
	 */
	public static final native long deallocate_cef_life_span_handler_t(
			final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by allocate_cef_load_handler_t
	 */
	public static final native long deallocate_cef_load_handler_t(
			final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by allocate_cef_request_handler_t
	 */
	public static final native long deallocate_cef_request_handler_t(
			final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by
	 *            allocate_cef_resource_handler_t
	 */
	public static final native long deallocate_cef_resource_handler_t(
			final long object);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by
	 *            allocate_cef_scheme_handler_factory_t
	 */
	public static final native void deallocate_cef_scheme_handler_factory_t(
			long cefSchemeHandlerFactory);

	/**
	 * Deallocates memory
	 * 
	 * @param object
	 *            the memory address allocated by allocate_cef_string_visitor_t
	 */
	public static final native void deallocate_cef_string_visitor_t(
			long cefSchemeHandlerFactory);

	/**
	 * @param dest
	 *            cast=(void *)
	 * @param src
	 *            cast=(const void *),flags=no_out
	 */
	public static final native void memmove(cef_popup_features_t dest,
			long src);
}
