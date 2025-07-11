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
#[cfg(feature = "gen")]
extern crate bindgen;
#[cfg(feature = "gen")]
use bindgen::callbacks::DeriveInfo;
#[cfg(feature = "gen")]
use bindgen::Formatter;

#[cfg(feature = "gen")]
fn main() {
    let cef_path = get_cef_path();
    gen_cef(cef_path.display());
    gen_os(cef_path.display());
}

#[cfg(not(any(feature = "gen")))]
fn main() {}

#[cfg(feature = "gen")]
fn get_cef_path() -> std::path::PathBuf {
    let cwd = std::env::current_dir().unwrap();
    let mut cef_path = cwd.clone();
    cef_path.push("..");
    cef_path.push("..");
    cef_path.push("cef");
    cef_path
}

#[cfg(feature = "gen")]
fn gen_os(cef_path: std::path::Display) {
    let _ = generator(cef_path)
        .header("cef_win.h")
        .allowlist_type("_cef_main_args_t")
        .allowlist_type("_cef_window_info_t")
        .blocklist_type("wchar_t")
        .blocklist_type("char16")
        .blocklist_type(".*string.*")
        .raw_line("use crate::cef::cef_string_t;")
        .generate()
        .expect("Failed to gencef win")
        .write_to_file(std::path::Path::new("src").join("cef").join("win.rs"));
}

#[cfg(feature = "gen")]
fn gen_cef(cef_path: std::path::Display) {
    use std::io::Write;
    let gen = generator(cef_path).header("include/internal/cef_types_win.h");
    let generated = gen
        .header("cef.h")
        .allowlist_type("cef_string_t")
        .allowlist_type("cef_string_userfree_t")
        .allowlist_type(".*cef_base_t")
        .allowlist_type("_cef_scheme_registrar_t")
        .allowlist_type("_cef_.*_handler_t")
        .allowlist_type("_cef_urlrequest_client_t")
        .allowlist_type("_cef_urlrequest_t")
        .allowlist_type("cef_window_handle_t")
        .allowlist_function("cef_string_.*")
        .allowlist_function("cef_execute_process")
        .allowlist_function("cef_initialize")
        .allowlist_function("cef_run_message_loop")
        .allowlist_function("cef_shutdown")
        .allowlist_function("cef_browser_host_create_browser")
        .allowlist_function("cef_urlrequest_create")
        .allowlist_function("cef_cookie_manager_get_global_manager")
        .allowlist_function("cef_.*")
        .blocklist_type("_cef_main_args_t")
        .blocklist_type("_cef_window_info_t")
        .blocklist_type("(__)?time(64)?_t")
        .blocklist_type("wchar_t")
        .blocklist_type("char16")
        .blocklist_type("u?int64")
        .blocklist_type("DWORD")
        .blocklist_type("HWND.*")
        .blocklist_type("HINSTANCE.*")
        .blocklist_type("HMENU.*")
        .blocklist_type("HICON.*")
        .blocklist_type("HCURSOR.*")
        .blocklist_type("POINT")
        .blocklist_type("MSG")
        .blocklist_type("tagMSG")
        .blocklist_type("tagPOINT")
        .blocklist_type(".*XDisplay")
        .blocklist_type("VisualID")
        .blocklist_type(".*XEvent")
        .raw_line("pub mod win;")
        .raw_line("pub use self::win::_cef_window_info_t;")
        .raw_line("pub use self::win::_cef_main_args_t;")
        .raw_line("pub type wchar_t = u16;")
        .raw_line("pub type char16 = u16;")
        .raw_line("pub type time_t = i64;")
        .raw_line("pub type int64 = ::std::os::raw::c_longlong;")
        .raw_line("pub type uint64 = ::std::os::raw::c_ulonglong;")
        .generate()
        .expect("Failed to gencef")
        .to_string();
    let new_data = generated.replace("\"stdcall\"", "\"system\"");

    // Recreate the file and dump the processed contents to it
    let mut dst = std::fs::File::create(std::path::Path::new("src").join("cef").join("mod.rs"))
        .expect("Cannot create mod.rs file");
    dst.write_all(new_data.as_bytes())
        .expect("Cannot write mod.rs");
}

#[cfg(feature = "gen")]
#[repr(C)]
#[derive(Debug)]
struct ToJavaCallbacks();

#[cfg(feature = "gen")]
impl bindgen::callbacks::ParseCallbacks for ToJavaCallbacks {
    fn add_derives(&self, info: &DeriveInfo) -> Vec<String> {
        let name = info.name;
        if vec![
            "_cef_app_t",
            "_cef_request_handler_t",
            "_cef_load_handler_t",
            "_cef_life_span_handler_t",
            "_cef_jsdialog_handler_t",
            "_cef_focus_handler_t",
            "_cef_display_handler_t",
            "_cef_browser_process_handler_t",
            "_cef_context_menu_handler_t",
            "_cef_client_t",
            "_cef_browser_process_handler_t",
            "_cef_download_handler_t",
            "_cef_string_visitor_t",
            "_cef_cookie_visitor_t",
            "_cef_resource_handler_t",
            "_cef_scheme_handler_factory_t",
        ]
        .contains(&name)
        {
            return vec!["JNICEFCallback".into()];
        }

        if ["_cef_base_ref_counted_t"].contains(&name) {
            return vec!["FromJava".into()];
        }

        vec![]
    }
}

#[cfg(feature = "gen")]
fn generator(cef_path: std::path::Display) -> bindgen::Builder {
    let mut config = bindgen::CodegenConfig::FUNCTIONS;
    config.insert(bindgen::CodegenConfig::TYPES);
    let callbacks = ToJavaCallbacks();
    bindgen::builder()
        .clang_arg(format!("-I{cef_path}"))
        .clang_arg(format!(
            "-I{}",
            "C:\\Program Files (x86)\\Microsoft SDKs\\Windows\\v7.1A\\Include"
        ))
        .clang_arg("-fparse-all-comments")
        .clang_arg("-Wno-nonportable-include-path")
        .clang_arg("-Wno-invalid-token-paste")
        .parse_callbacks(Box::new(callbacks))
        .with_codegen_config(config)
        .rustified_enum(".*")
        .formatter(Formatter::Rustfmt)
        .derive_debug(true)
        .trust_clang_mangling(false)
        .layout_tests(false)
        .size_t_is_usize(true)
        .raw_line("#![allow(dead_code)]")
        .raw_line("#![allow(non_snake_case)]")
        .raw_line("#![allow(non_camel_case_types)]")
        .raw_line("#![allow(non_upper_case_globals)]")
        .raw_line("#![allow(unused_imports)]")
        .raw_line("use chromium_jni_macro::FromJava;")
        .raw_line("use chromium_jni_utils::FromJava;")
        .raw_line("use chromium_jni_utils::FromJavaMember;")
        .raw_line("use chromium_jni_macro::JNICEFCallback;")
        .raw_line("use chromium_jni_utils::JNICEFCallback;")
        .raw_line("use chromium_jni_utils::JNIWrapperType;")
        .raw_line("use jni::JNIEnv;")
        .raw_line("use crate::ToJava;")
        .raw_line("use jni::objects::GlobalRef;")
        .raw_line("use jni::objects::JObject;")
        .raw_line("use chromium_jni_utils::jni_unwrap;")
}
