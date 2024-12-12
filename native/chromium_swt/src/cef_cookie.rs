/**
 * Copyright (c) 2022 DB Netz AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
use chromium_jni_macro::jni_wrapper;
use jni::objects::JClass;
use jni::sys::jstring;
use jni::JNIEnv;
use std::ffi::CStr;
use std::os::raw::{c_char, c_int};

#[no_mangle]
pub extern "C" fn Java_org_eclipse_set_browser_lib_cefswt_1cookie_1visitor_1t_cefswt_1cookie_1value(
    env: JNIEnv,
    _class: JClass,
    cookie: *mut chromium::cef::_cef_cookie_t,
) -> jstring {
    let value = unsafe { CStr::from_ptr(cefswt_cookie_value(cookie)) };
    env
        .new_string(value.to_str().unwrap())
        .unwrap()
        .into_inner()
}

#[no_mangle]
pub extern "C" fn Java_org_eclipse_set_browser_lib_cefswt_1cookie_1visitor_1t_cefswt_1cookie_1to_1java(
    env: JNIEnv,
    _class: JClass,
    cookie: *mut chromium::cef::_cef_cookie_t,
) -> jstring {
    let value = unsafe { CStr::from_ptr(cefswt_cookie_to_java(cookie)) };
    env
        .new_string(value.to_str().unwrap())
        .unwrap()
        .into_inner()
}

fn cefswt_cookie_to_java(cookie: *mut chromium::cef::_cef_cookie_t) -> *mut c_char {
    let name = unsafe { (*cookie).name };
    chromium::utils::cstr_from_cef(&name)
}

#[jni_wrapper("org.eclipse.set.browser.lib.cefswt_cookie_visitor_t")]
pub fn cefswt_set_cookie(
    jurl: *const c_char,
    jname: *const c_char,
    jvalue: *const c_char,
    jdomain: *const c_char,
    jpath: *const c_char,
    secure: i32,
    httponly: i32,
    max_age: f64,
) -> c_int {
    let manager =
        unsafe { chromium::cef::cef_cookie_manager_get_global_manager(std::ptr::null_mut()) };
    let url = chromium::utils::cef_string_from_c(jurl);
    let domain = chromium::utils::cef_string_from_c(jdomain);
    let path = chromium::utils::cef_string_from_c(jpath);
    let name = chromium::utils::cef_string_from_c(jname);
    let value = chromium::utils::cef_string_from_c(jvalue);
    let has_expires = if max_age == -1.0 { 0 } else { 1 };
    let mut expires = chromium::cef::cef_time_t {
        year: 0,
        month: 0,
        day_of_week: 0,
        day_of_month: 0,
        hour: 0,
        minute: 0,
        second: 0,
        millisecond: 0,
    };

    let mut expires_bt = chromium::cef::cef_basetime_t { val: 0 };
    if max_age != -1.0 {
        unsafe {
            chromium::cef::cef_time_from_doublet(max_age, &mut expires);
            chromium::cef::cef_time_to_basetime(&expires, &mut expires_bt);
        };
    }

    let cookie = chromium::cef::_cef_cookie_t {
        name,
        value,
        domain,
        path,
        secure,
        httponly,
        creation: chromium::cef::cef_basetime_t {
            val: expires_bt.val,
        },
        last_access: chromium::cef::cef_basetime_t {
            val: expires_bt.val,
        },
        has_expires,
        expires: chromium::cef::cef_basetime_t {
            val: expires_bt.val,
        },
        same_site: chromium::cef::cef_cookie_same_site_t::CEF_COOKIE_SAME_SITE_NO_RESTRICTION,
        priority: chromium::cef::cef_cookie_priority_t::CEF_COOKIE_PRIORITY_MEDIUM,
    };
    unsafe {
        (*manager).set_cookie.expect("null set_cookie")(
            manager,
            &url,
            &cookie,
            std::ptr::null_mut(),
        )
    }
}

#[jni_wrapper("org.eclipse.set.browser.lib.cefswt_cookie_visitor_t")]
pub fn cefswt_get_cookie(
    jurl: *const c_char,
    jvisitor: *mut chromium::cef::_cef_cookie_visitor_t,
) -> c_int {
    let manager =
        unsafe { chromium::cef::cef_cookie_manager_get_global_manager(std::ptr::null_mut()) };
    let url = chromium::utils::cef_string_from_c(jurl);

    unsafe {
        (*manager)
            .visit_url_cookies
            .expect("null visit_url_cookies")(manager, &url, 1, jvisitor)
    }
}

pub unsafe fn cefswt_cookie_value(cookie: *mut chromium::cef::_cef_cookie_t) -> *mut c_char {
    chromium::utils::cstr_from_cef(&(*cookie).value)
}

#[jni_wrapper("org.eclipse.set.browser.lib.cefswt_cookie_visitor_t")]
pub fn cefswt_delete_cookies() {
    let manager =
        unsafe { chromium::cef::cef_cookie_manager_get_global_manager(std::ptr::null_mut()) };
    unsafe {
        (*manager).delete_cookies.expect("null delete_cookies")(
            manager,
            std::ptr::null_mut(),
            std::ptr::null_mut(),
            std::ptr::null_mut(),
        )
    };
}
