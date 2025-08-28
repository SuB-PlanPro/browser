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
use std::{
    ffi::c_void,
    os::raw::{c_char, c_int},
};

use chromium::cef;
use chromium_jni_macro::jni_wrapper;

use crate::get_browser_host;

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_reload(browser: *mut cef::cef_browser_t) {
    unsafe {
        (*browser).reload.expect("null reload")(browser);
    };
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_get_text(browser: *mut cef::cef_browser_t, visitor: *mut cef::_cef_string_visitor_t) {
    let get_frame = unsafe { (*browser).get_main_frame.expect("null get_main_frame") };
    let main_frame = unsafe { get_frame(browser) };
    assert!(!main_frame.is_null());
    let get_text = unsafe { (*main_frame).get_source.expect("null get_text") };
    unsafe { get_text(main_frame, visitor) };
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_execute(browser: *mut cef::cef_browser_t, text: *const c_char) {
    let text = chromium::utils::str_from_c(text);
    let text_cef = chromium::utils::cef_string(text);
    let url_cef = chromium::utils::cef_string_empty();
    let get_frame = unsafe { (*browser).get_main_frame.expect("null get_main_frame") };
    let main_frame = unsafe { get_frame(browser) };
    let execute = unsafe {
        (*main_frame)
            .execute_java_script
            .expect("null execute_java_script")
    };
    unsafe { execute(main_frame, &text_cef, &url_cef, 0) };
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_eval(
    browser: *mut cef::cef_browser_t,
    text: *const c_char,
    id: i32,
    callback: unsafe extern "system" fn(work: c_int, kind: c_int, value: *const c_char),
) -> c_int {
    let text_cef = chromium::utils::cef_string_from_c(text);
    let name = chromium::utils::cef_string("eval");
    unsafe {
        let msg = cef::cef_process_message_create(&name);
        let args = (*msg).get_argument_list.unwrap()(msg);
        let s = (*args).set_int.unwrap()(args, 1, id);
        assert_eq!(s, 1);
        let s = (*args).set_string.unwrap()(args, 2, &text_cef);
        assert_eq!(s, 1);
        match chromium_subp::socket::wait_response(
            browser,
            msg,
            args,
            cef::cef_process_id_t::PID_RENDERER,
            Some(callback),
        ) {
            Ok(r) => {
                callback(0, r.kind as i32, r.str_value.as_ptr());
                1
            }
            Err(_e) => 0,
        }
    }
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_function(browser: *mut cef::cef_browser_t, name: *const c_char, id: i32) {
    let name_cef = chromium::utils::cef_string_from_c(name);
    let msg_name = chromium::utils::cef_string("function");
    unsafe {
        let msg = cef::cef_process_message_create(&msg_name);
        let args = (*msg).get_argument_list.unwrap()(msg);
        let s = (*args).set_int.unwrap()(args, 0, id);
        assert_eq!(s, 1);
        let s = (*args).set_string.unwrap()(args, 1, &name_cef);
        assert_eq!(s, 1);
        let frame = (*browser).get_main_frame.unwrap()(browser);

        (*frame).send_process_message.unwrap()(frame, cef::cef_process_id_t::PID_RENDERER, msg);
    }
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_stop(browser: *mut cef::cef_browser_t) {
    unsafe {
        (*browser).stop_load.expect("null stop_load")(browser);
    };
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_free(_obj: *mut cef::cef_browser_t) {
    // IMPROVE: Why is this function empty?
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_get_id(browser: *mut cef::cef_browser_t) -> c_int {
    unsafe {
        let get_id = (*browser).get_identifier.unwrap();
        get_id(browser)
    }
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_resized(browser: *mut cef::cef_browser_t, width: i32, height: i32) {
    let browser_host = get_browser_host(browser);
    let get_window_handle_fn = unsafe {
        (*browser_host)
            .get_window_handle
            .expect("no get_window_handle")
    };
    let win_handle = unsafe { get_window_handle_fn(browser_host) };
    do_resize(win_handle, width, height);
}

fn do_resize(win_handle: *mut c_void, width: i32, height: i32) {
    let x = 0;
    let y = 0;
    unsafe {
        winapi::um::winuser::SetWindowPos(
            win_handle as winapi::shared::windef::HWND,
            std::ptr::null_mut(),
            x,
            y,
            width,
            height,
            winapi::um::winuser::SWP_NOZORDER,
        )
    };
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_close_browser(browser: *mut cef::cef_browser_t, force: c_int) {
    let browser_host = get_browser_host(browser);
    let close_fn = unsafe { (*browser_host).close_browser.expect("null close_browser") };
    unsafe { close_fn(browser_host, force) };
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
#[allow(clippy::unnecessary_unwrap)]
pub fn cefswt_load_url(
    browser: *mut cef::cef_browser_t,
    url: *const c_char,
    post_bytes: Option<Vec<u8>>,
    post_size: usize,
    headers: *const c_char,
    headers_size: usize,
) {
    let url = chromium::utils::str_from_c(url);
    let url_cef = chromium::utils::cef_string(url);
    unsafe {
        let get_frame = (*browser).get_main_frame.expect("null get_main_frame");
        let main_frame = get_frame(browser);
        if post_bytes.is_none() && headers.is_null() {
            (*main_frame).load_url.unwrap()(main_frame, &url_cef);
        } else {
            let request = cef::cef_request_create();
            (*request).set_url.unwrap()(request, &url_cef);
            if post_bytes.is_some() {
                let post_data = cef::cef_post_data_create();
                let post_element = cef::cef_post_data_element_create();
                (*post_element).set_to_bytes.unwrap()(
                    post_element,
                    post_size,
                    post_bytes.unwrap().as_ptr() as *const c_void,
                );
                (*post_data).add_element.unwrap()(post_data, post_element);

                (*request).set_post_data.unwrap()(request, post_data);
            }

            if !headers.is_null() {
                let map = cef::cef_string_multimap_alloc();

                let headers = chromium::utils::str_from_c(headers);
                let headers: Vec<&str> = headers.splitn(headers_size, "::").collect();
                for header_str in headers.iter().take(headers_size) {
                    let header: Vec<&str> = header_str.splitn(2, ':').collect();
                    let key = header[0].trim();
                    let value = header[1].trim();
                    let key = chromium::utils::cef_string(key);
                    let value = chromium::utils::cef_string(value);

                    cef::cef_string_multimap_append(map, &key, &value);
                }
                (*request).set_header_map.unwrap()(request, map);
            }

            (*main_frame).load_request.unwrap()(main_frame, request);
        }
    }
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_get_url(browser: *mut cef::cef_browser_t) -> cef::cef_string_userfree_t {
    let get_frame = unsafe { (*browser).get_main_frame.expect("null get_main_frame") };
    let main_frame = unsafe { get_frame(browser) };
    assert!(!main_frame.is_null());
    let get_url = unsafe { (*main_frame).get_url.expect("null get_url") };
    unsafe { get_url(main_frame) }
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_browser_t")]
pub fn cefswt_set_focus(browser: *mut cef::cef_browser_t, set: bool, parent: *mut c_void) {
    let browser_host = get_browser_host(browser);
    let focus_fn = unsafe { (*browser_host).set_focus.expect("null set_focus") };
    let focus = if set { 1 } else { 0 };
    unsafe { focus_fn(browser_host, focus) };
    if !set && !parent.is_null() {
        do_set_focus(parent, focus);
    }
}

fn do_set_focus(_parent: *mut c_void, _focus: i32) {
    // TODO
}
