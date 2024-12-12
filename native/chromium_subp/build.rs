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
fn main() {
    link();
}

fn get_cef_path() -> std::path::PathBuf {
    let mut cef_path = std::env::current_dir().unwrap();
    cef_path.push("..");
    cef_path.push("..");
    cef_path.push("cef");
    cef_path.push("Release");
    cef_path
}

fn link() {
    let cef_path = get_cef_path();

    if !cef_path.exists() {
        panic!(
            "cargo:warning=Extract and rename cef binary (minimal) distro to {:?}",
            cef_path
        );
    }

    // Tell cargo to tell rustc to link the system shared library.
    println!("cargo:rustc-link-search={}", cef_path.display());
    println!("cargo:rustc-link-lib=libcef");

    // Set up compiler flags to use the Windows subsystem & Windows CRT
    #[cfg(target_env = "msvc")]
    println!("cargo:rustc-link-arg=/SUBSYSTEM:WINDOWS");
    #[cfg(target_env = "msvc")]
    println!("cargo:rustc-link-arg=/ENTRY:mainCRTStartup");
    #[cfg(target_env = "gnu")]
    println!("cargo:rustc-link-arg=-Wl,-subsystem,windows");
}
