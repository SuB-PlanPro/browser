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
package org.eclipse.set.browser.lib;

import org.eclipse.swt.internal.C;

///
/// Structure used to implement browser process callbacks. The functions of this
/// structure will be called on the browser process main thread unless otherwise
/// indicated.
///
@SuppressWarnings("javadoc")
public class cef_browser_process_handler_t extends CStruct {
	public static final int sizeof = ChromiumLib
			.cef_browser_process_handler_t_sizeof();
	///
	/// Base structure.
	///
	public cef_base_ref_counted_t base;

	public long get_default_client;
	///
	/// Called before a child process is launched. Will be called on the browser
	/// process UI thread when launching a render process and on the browser
	/// process IO thread when launching a GPU or plugin process. Provides an
	/// opportunity to modify the child process command line. Do not keep a
	/// reference to |command_line| outside of this function.
	///
	/** @field cast=(void*) */
	public long on_before_child_process_launch;

	///
	/// Called on the browser process UI thread immediately after the CEF
	/// context
	/// has been initialized.
	///
	/** @field cast=(void*) */
	public long on_context_initialized;

	///
	/// Called from any thread when work has been scheduled for the browser
	/// process
	/// main (UI) thread. This callback is used in combination with CefSettings.
	/// external_message_pump and cef_do_message_loop_work() in cases where the
	/// CEF
	/// message loop must be integrated into an existing application message
	/// loop
	/// (see additional comments and warnings on CefDoMessageLoopWork). This
	/// callback should schedule a cef_do_message_loop_work() call to happen on
	/// the
	/// main (UI) thread. |delay_ms| is the requested delay in milliseconds. If
	/// |delay_ms| is <= 0 then the call should happen reasonably soon. If
	/// |delay_ms| is > 0 then the call should be scheduled to happen after the
	/// specified delay and any currently pending scheduled call should be
	/// cancelled.
	///
	/** @field cast=(void*) */
	public long on_schedule_message_pump_work;

	public cef_browser_process_handler_t() {
		base = new cef_base_ref_counted_t(sizeof);
	}

	@Override
	public void allocate() {
		ptr = C.malloc(sizeof);
		ChromiumLib.memmove(ptr, this);
	}

}
