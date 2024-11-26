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

import org.eclipse.set.browser.lib.ChromiumLib;
import org.eclipse.swt.widgets.Display;

/**
 * Wrapper for the CEF message loop
 * 
 * @author Stuecker
 */
public class MessageLoop {
	private static final int LOOP = 5;

	private boolean loopDisable;

	private boolean loopShutdown = false;
	private Runnable loopWork;
	private final Runnable loopWorkRunnable = () -> {
		final Display display = Display.getCurrent();
		if (display == null || display.isDisposed()) {
			return;
		}
		loop_work();
	};
	private boolean pumpDisable;

	/**
	 * Disables message pumping until the next message loop cycle
	 */
	public void disablePump() {
		pumpDisable = true;
	}

	/**
	 * Pauses the message loop
	 */
	public void pause() {
		loopDisable = true;
	}

	/**
	 * Restarts the message loop with a delay
	 * 
	 * @param display
	 *            the display
	 * @param ms
	 *            the delay after which to restart the message loop
	 */
	public void restartLoop(final Display display, final int ms) {
		if (loopWork != null) {
			display.timerExec(-1, loopWork);
			display.timerExec(LOOP + ms, loopWork);
		}
	}

	/**
	 * Note: May be called from other threads
	 * 
	 * @param delay
	 *            delay after which the message pump should be scheduled
	 */
	public void scheduleMessagePumpWork(final int delay) {
		if (pumpDisable) {
			return;
		}

		final Display display = Display.getDefault();

		final Runnable scheduleWork = () -> {
			restartLoop(display, delay);
			display.timerExec(-1, loopWorkRunnable);
			display.timerExec(delay, loopWorkRunnable);
		};

		if (Display.getCurrent() != null) {
			if (delay <= 0) {
				restartLoop(display, 0);
				display.asyncExec(loopWorkRunnable);
			} else {
				scheduleWork.run();
			}
		} else {
			if (delay <= 0) {
				display.asyncExec(() -> {
					restartLoop(display, 0);
					loopWorkRunnable.run();
				});
			} else {
				display.asyncExec(scheduleWork);
			}
		}

	}

	/**
	 * Exits the loop with the next iteration
	 */
	public void shutdown() {
		loopShutdown = true;
	}

	/**
	 * Starts the loop
	 */
	public void start() {
		loopWork = () -> {
			if (!loopShutdown && !Display.getDefault().isDisposed()) {
				loop_work();
				Display.getDefault().timerExec(LOOP, loopWork);
			}
		};
		Display.getDefault().timerExec(LOOP, loopWork);
	}

	/**
	 * Unpauses the loop
	 */
	public void unpause() {
		loopDisable = false;
	}

	private void loop_work() {
		if (!loopDisable) {
			if (ChromiumLib.cefswt_do_message_loop_work() == 0) {
				System.err.println("error looping chromium");
			}
			if (pumpDisable) {
				pumpDisable = false;
			}
		}
	}
}
