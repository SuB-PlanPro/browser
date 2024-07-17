/**
 * Copyright (c) 2022 DB Netz AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package org.eclipse.set.browser.lib;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.set.browser.cef.win32.CEFResource;

/**
 * Helper class for accessing and loading CEF Binaries
 * 
 * @author Stuecker
 */
public class CEFLibrary {
	private static final String CHROME_ELF = "chrome_elf";
	private static final String CHROMIUM_SUBPROCESS = "chromium_subp.exe";
	private static final String JNI_LIB = "chromium_jni";
	private static final String LIBCEF = "libcef";

	/**
	 * @return the path to the Chromium subprocess binary
	 */
	public static String getSubprocessExePath() {
		final URI uri = getResourceFileURI(CHROMIUM_SUBPROCESS);
		return Paths.get(uri).toAbsolutePath().toString();
	}

	/**
	 * @return a path for temporary files for the browser
	 */
	public static String getTempPath() {
		return getTempPathBase().resolve(Long.toString(ProcessHandle.current().pid())).toString();
	}
	
	public static Path getTempPathBase() {
		return Paths.get(System.getenv("APPDATA"), "Eclipse SET", "cef_temp")
				.toAbsolutePath();
	}
	
	public static void cleanTempPath() throws IOException {
		Path path = getTempPathBase();
		if (!Files.exists(path)) {
			return;
		}
 
		try (final Stream<Path> files = Files.list(path)) {
			files.filter(p -> {
				// Only consider directories
				if (!p.toFile().isDirectory()) {
					return false;
				}

				try {
					// Check if process is still running, but not this process
					// This may find another process, with a reused PID, however
					// this acceptable, as over time all directories will be
					// cleaned up
					final long pid = Long.parseLong(p.getFileName().toString());
					return ProcessHandle.current().pid() == pid || ProcessHandle.of(pid).isEmpty();
				} catch (NumberFormatException e) {
					return true;
				}
			}).forEach(p -> {
				try {
					FileUtils.deleteDirectory(p.toFile());
				} catch (final IOException e) {
					// ignore failed deletion
				}
			});
		}
	}

	/**
	 * Loads the CEF Libraries
	 * 
	 * Make sure to call init() beforehand
	 */
	public static void loadLibraries() {
		System.load(getCEFLibraryFile(CHROME_ELF).getAbsolutePath());
		System.load(getCEFLibraryFile(LIBCEF).getAbsolutePath());
		System.load(getLibraryFile(JNI_LIB).getAbsolutePath());
	}

	private static File getCEFLibraryFile(final String library) {
		final String mapLibraryName = System.mapLibraryName(library);
		return CEFResource.getPath().resolve(mapLibraryName).toFile();
	}

	private static File getLibraryFile(final String library) {
		final String mapLibraryName = System.mapLibraryName(library);
		return new File(getResourceFileURI(mapLibraryName));
	}

	private static URI getResourceFileURI(final String name) {
		final URL url = CEFLibrary.class.getClassLoader().getResource(name);
		try {
			// Ensure we have a file url (and not a bundle URL)
			final URL fileUrl = FileLocator.toFileURL(url);
			// Transform into URI. Avoid using URL#toURI() as it does not handle
			// special characters correctly
			return new URI(fileUrl.getProtocol(), fileUrl.getUserInfo(),
					fileUrl.getHost(), fileUrl.getPort(), fileUrl.getPath(),
					fileUrl.getQuery(), fileUrl.getRef());

		} catch (final IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

}
