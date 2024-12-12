/**
 * Copyright (c) 2022 DB Netz AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
use chromium_jni_macro::jni_name;
use chromium_jni_macro::jni_wrapper;
use jni::objects::JClass;
use jni::objects::JString;
use jni::sys::jboolean;
use jni::sys::jstring;
use jni::JNIEnv;
use std::ffi::CStr;

#[jni_name("org.eclipse.set.browser.lib.cef_download_item_t")]
pub unsafe extern "C" fn get_full_path(
    env: JNIEnv,
    _class: JClass,
    item: *mut chromium::cef::_cef_download_item_t,
) -> jstring {
    let path = (*item).get_full_path.unwrap()(item);

    let value = CStr::from_ptr(crate::cefswt_cefstring_to_java(path));
    env
        .new_string(value.to_str().unwrap())
        .unwrap()
        .into_inner()
}

#[jni_name("org.eclipse.set.browser.lib.cef_download_item_t")]
pub unsafe extern "C" fn is_cancelled(
    _env: JNIEnv,
    _class: JClass,
    item: *mut chromium::cef::_cef_download_item_t,
) -> jboolean {
    (*item).is_canceled.unwrap()(item) as jboolean
}

#[jni_name("org.eclipse.set.browser.lib.cef_download_item_t")]
pub unsafe extern "C" fn is_complete(
    _env: JNIEnv,
    _class: JClass,
    item: *mut chromium::cef::_cef_download_item_t,
) -> jboolean {
    (*item).is_complete.unwrap()(item) as jboolean
}

#[jni_name("org.eclipse.set.browser.lib.cef_download_item_t")]
pub unsafe extern "C" fn get_url(
    env: JNIEnv,
    _class: JClass,
    item: *mut chromium::cef::_cef_download_item_t,
) -> jstring {
    let url = (*item).get_url.unwrap()(item);
    let value = CStr::from_ptr(crate::cefswt_cefstring_to_java(url));
    env
        .new_string(value.to_str().unwrap())
        .unwrap()
        .into_inner()
}

#[jni_name("org.eclipse.set.browser.lib.cef_download_item_t")]
pub unsafe extern "C" fn before_download_callback(
    _env: JNIEnv,
    _class: JClass,
    callback: *mut chromium::cef::_cef_before_download_callback_t,
    jpath: JString,
) {
    let rpath = _env
        .get_string_utf_chars(jpath)
        .unwrap_or(std::ptr::null_mut());

    let path = chromium::utils::cef_string_from_c(rpath);
    (*callback).cont.unwrap()(callback, &path, 0);

    _env.release_string_utf_chars(jpath, rpath).unwrap();
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_download_item_t")]
pub fn cefswt_copy_bytes(destination: *mut u8, source: Vec<u8>, count: usize) {
    unsafe {
        std::ptr::copy(source.as_ptr(), destination, count);
    }
}

#[jni_wrapper("org.eclipse.set.browser.lib.cef_download_item_t")]
pub fn cefswt_response_set_status_code(response: *mut chromium::cef::cef_response_t, status: i32) {
    unsafe {
        (*response).set_status.unwrap()(response, status);
    }
}
