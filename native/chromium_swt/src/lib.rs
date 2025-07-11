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
extern crate chromium;
use chromium::cef;
use chromium_jni_macro::{jni_name, jni_wrapper};

mod app;
pub mod cef_browser;
pub mod cef_cookie;
pub mod cef_download_item;
pub mod cef_request;
pub mod cef_response;

use std::{
    ffi::CStr,
    os::raw::{c_char, c_int, c_void},
};

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_init(
    japp: *mut cef::cef_app_t,
    subp_path: *const c_char,
    cef_path: *const c_char,
    temp_path: *const c_char,
    user_agent_product: *const c_char,
    locale: *const c_char,
    debug_port: c_int,
    log_path: *const c_char,
    log_level: c_int,
) {
    let subp_path = chromium::utils::str_from_c(subp_path);
    let cef_path = chromium::utils::str_from_c(cef_path);
    let temp_path = chromium::utils::str_from_c(temp_path);
    let log_path = chromium::utils::str_from_c(log_path);
    let log_level = unsafe { std::mem::transmute(log_level) };

    let main_args = chromium::utils::prepare_args();

    let subp = std::path::Path::new(&subp_path);
    let cef_dir = std::path::Path::new(&cef_path);
    let temp_dir = std::path::Path::new(&temp_path);

    let subp_cef = chromium::utils::cef_string(subp.to_str().unwrap());

    if cef_path != subp_path {
        set_env_var(cef_path, "PATH", ";");
    }

    let resources_cef = chromium::utils::cef_string(cef_dir.to_str().unwrap());
    let locales_cef = chromium::utils::cef_string(cef_dir.join("locales").to_str().unwrap());
    let framework_dir_cef = chromium::utils::cef_string_empty();
    let cache_dir_cef = chromium::utils::cef_string(temp_dir.join("cef_cache").to_str().unwrap());
    let log_path = chromium::utils::cef_string(log_path);

    let settings = cef::_cef_settings_t {
        size: std::mem::size_of::<cef::_cef_settings_t>(),
        no_sandbox: 1,
        browser_subprocess_path: subp_cef,
        framework_dir_path: framework_dir_cef,
        multi_threaded_message_loop: 0,
        external_message_pump: 1,
        windowless_rendering_enabled: 0,
        command_line_args_disabled: 0,
        cache_path: cache_dir_cef,
        root_cache_path: cache_dir_cef,
        persist_session_cookies: 1,
        persist_user_preferences: 1,
        user_agent: chromium::utils::cef_string_empty(),
        locale: chromium::utils::cef_string_from_c(locale),
        log_file: log_path,
        log_severity: log_level,
        javascript_flags: chromium::utils::cef_string_empty(),
        resources_dir_path: resources_cef,
        locales_dir_path: locales_cef,
        pack_loading_disabled: 0,
        remote_debugging_port: debug_port,
        uncaught_exception_stack_size: 0,
        background_color: 0,
        accept_language_list: chromium::utils::cef_string_empty(),
        main_bundle_path: chromium::utils::cef_string_empty(),
        chrome_runtime: 0,
        user_agent_product: chromium::utils::cef_string_from_c(user_agent_product),
        cookieable_schemes_list: chromium::utils::cef_string_empty(),
        cookieable_schemes_exclude_defaults: 0,
        log_items: chromium::cef::cef_log_items_t::LOG_ITEMS_DEFAULT,
        chrome_policy_id: chromium::utils::cef_string_empty(),
        chrome_app_icon_id: 0,
    };

    do_initialize(main_args, settings, japp);
}

fn set_env_var(cef_path: &str, var: &str, sep: &str) {
    match std::env::var(var) {
        Ok(paths) => {
            let paths = format!("{cef_path}{sep}{paths}");
            std::env::set_var(var, paths);
        }
        Err(e) => {
            println!("Couldn't read {var} ({e})");
            std::env::set_var(var, cef_path);
        }
    };
}

fn do_initialize(
    main_args: cef::_cef_main_args_t,
    settings: cef::_cef_settings_t,
    app_raw: *mut cef::_cef_app_t,
) {
    unsafe { cef::cef_initialize(&main_args, &settings, &mut (*app_raw), std::ptr::null_mut()) };
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_create_browser(
    hwnd: *mut c_void,
    url: *const c_char,
    client: &mut cef::_cef_client_t,
    w: c_int,
    h: c_int,
    js: c_int,
    cbg: c_int,
) -> *const cef::cef_browser_t {
    let bg: cef::cef_color_t = cbg as cef::cef_color_t;
    let url = chromium::utils::str_from_c(url);
    app::create_browser(hwnd, url, client, w, h, js, bg)
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_set_window_info_parent(
    window_info: *mut cef::_cef_window_info_t,
    client: *mut *mut cef::_cef_client_t,
    jclient: *mut cef::_cef_client_t,
    hwnd: *mut c_void,
    x: c_int,
    y: c_int,
    w: c_int,
    h: c_int,
) {
    unsafe {
        (*client) = jclient;
        app::set_window_parent(window_info, hwnd, x, y, w, h);
    };
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_do_message_loop_work() -> c_int {
    let result = std::panic::catch_unwind(|| {
        unsafe { cef::cef_do_message_loop_work() };
    });
    match result {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

#[repr(C)]
#[derive(Debug)]
pub struct FunctionSt {
    pub id: i32,
    pub port: i32,
    pub args: usize,
}

pub unsafe fn cefswt_function_id(message: *mut cef::cef_process_message_t, st: *mut FunctionSt) {
    let valid = (*message).is_valid.unwrap()(message);
    let name = (*message).get_name.unwrap()(message);
    (*st).id = -1;
    (*st).args = 0;
    (*st).port = 0;
    if valid == 1
        && cef::cef_string_utf16_cmp(&chromium::utils::cef_string("function_call"), name) == 0
    {
        let args = (*message).get_argument_list.unwrap()(message);
        let args_len = (*args).get_size.unwrap()(args);
        let port = (*args).get_int.unwrap()(args, 0);
        (*st).id = (*args).get_int.unwrap()(args, 1);
        (*st).args = (args_len - 1) / 2;
        (*st).port = port;
    }
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_function_arg(
    message: *mut cef::cef_process_message_t,
    index: i32,
    callback: unsafe extern "system" fn(work: c_int, kind: c_int, value: *const c_char),
) {
    unsafe {
        let args = (*message).get_argument_list.unwrap()(message);
        let kind = (*args).get_int.unwrap()(args, (1 + index * 2 + 1) as usize);
        let arg = (*args).get_string.unwrap()(args, (1 + index * 2 + 2) as usize);
        let cstr = chromium::utils::cstr_from_cef(arg);
        let kind = chromium_subp::socket::ReturnType::from(kind);
        callback(0, kind as i32, cstr);
    }
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_function_return(
    _browser: *mut cef::cef_browser_t,
    _id: i32,
    port: i32,
    kind: i32,
    ret: *const c_char,
) -> c_int {
    let cstr = unsafe { std::ffi::CStr::from_ptr(ret) };
    chromium_subp::socket::socket_client(port as u16, cstr.to_owned(), unsafe {
        std::mem::transmute(kind)
    })
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_is_same(browser: *mut cef::cef_browser_t, that: *mut cef::cef_browser_t) -> c_int {
    unsafe { (*browser).is_same.unwrap()(browser, that) }
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_dialog_close(
    callback: *mut cef::_cef_jsdialog_callback_t,
    success: c_int,
    prompt: *mut cef::cef_string_t,
) {
    unsafe { (*callback).cont.unwrap()(callback, success, prompt) };
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_context_menu_cancel(callback: *mut cef::_cef_run_context_menu_callback_t) {
    unsafe { (*callback).cancel.unwrap()(callback) };
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_auth_callback(
    callback: *mut cef::_cef_auth_callback_t,
    juser: *const c_char,
    jpass: *const c_char,
    cont: c_int,
) {
    unsafe {
        if cont == 1 {
            let user = chromium::utils::cef_string_from_c(juser);
            let pass = chromium::utils::cef_string_from_c(jpass);
            (*callback).cont.unwrap()(callback, &user, &pass)
        } else {
            (*callback).cancel.unwrap()(callback)
        }
    };
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_shutdown() {
    // Shut down CEF.
    unsafe { cef::cef_shutdown() };
}

fn get_browser_host(browser: *mut cef::cef_browser_t) -> *mut cef::_cef_browser_host_t {
    let get_host_fn = unsafe { (*browser).get_host.expect("null get_host") };
    unsafe { get_host_fn(browser) }
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_is_main_frame(frame: *mut cef::_cef_frame_t) -> i32 {
    unsafe { (*frame).is_main.expect("null is_main")(frame) }
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_go_forward(browser: *mut cef::_cef_browser_t) {
    unsafe { (*browser).go_forward.expect("null go_forward")(browser) };
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_go_back(browser: *mut cef::_cef_browser_t) {
    unsafe { (*browser).go_back.expect("null go_back")(browser) };
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_register_http_host(
    name: *const c_char,
    factory: *mut cef::_cef_scheme_handler_factory_t,
) {
    unsafe {
        let https = chromium::utils::cef_string("https");
        let http = chromium::utils::cef_string("http");
        let name = chromium::utils::cef_string_from_c(name);
        cef::cef_register_scheme_handler_factory(&http, &name, factory);
        cef::cef_register_scheme_handler_factory(&https, &name, factory);
    }
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_set_intptr(ptr: *mut ::std::os::raw::c_int, value: c_int) {
    unsafe {
        (*ptr) = value;
    }
}

#[jni_name("org.eclipse.set.browser.lib.ChromiumLib")]
#[no_mangle]
#[allow(clippy::not_unsafe_ptr_arg_deref)]
pub extern "C" fn cefswt_cstring_to_java(
    _env: jni::JNIEnv,
    _class: jni::objects::JClass,
    string: *const c_char,
) -> jni::sys::jstring {
    if string.is_null() {
        return std::ptr::null_mut();
    }
    let string = unsafe { CStr::from_ptr(string) };
    _env.new_string(string.to_str().unwrap())
        .unwrap()
        .into_inner()
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_cefstring_to_java(string: *mut chromium::cef::_cef_string_utf16_t) -> *mut c_char {
    chromium::utils::cstr_from_cef(string)
}

#[jni_wrapper("org.eclipse.set.browser.lib.ChromiumLib")]
pub fn cefswt_request_to_java(request: *mut chromium::cef::_cef_request_t) -> *mut c_char {
    let url = unsafe { (*request).get_url.expect("null get_url")(request) };
    let cstr = chromium::utils::cstr_from_cef(url);
    unsafe { cef::cef_string_userfree_utf16_free(url) };
    cstr
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_command_line_t")]
pub fn cefswt_append_switch(
    command_line: *mut chromium::cef::_cef_command_line_t,
    switch: *const c_char,
    value: *const c_char,
) {
    let switch = chromium::utils::cef_string_from_c(switch);

    if !value.is_null() {
        let value = chromium::utils::cef_string_from_c(value);
        unsafe {
            ((*command_line).append_switch_with_value.unwrap())(command_line, &switch, &value)
        }
    } else {
        unsafe { ((*command_line).append_switch.unwrap())(command_line, &switch) }
    }
}
