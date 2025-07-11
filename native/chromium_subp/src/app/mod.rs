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
use crate::socket;
use chromium::utils::cef_string;
use std::ffi::{CStr, CString};
use std::mem;
use std::os::raw::c_int;

pub fn new_cef_base_ref_counted(size: usize) -> chromium::cef::_cef_base_ref_counted_t {
    chromium::cef::_cef_base_ref_counted_t {
        size,
        add_ref: Option::None,
        has_one_ref: Option::None,
        release: Option::None,
        has_at_least_one_ref: Option::None,
    }
}

#[repr(C)]
pub struct App {
    cef: chromium::cef::_cef_app_t,
    render_process_handler: RenderProcessHandler,
}

impl Default for App {
    fn default() -> Self {
        Self::new()
    }
}
impl App {
    pub fn new() -> App {
        App {
            cef: App::cef_app(),
            render_process_handler: RenderProcessHandler::new(),
        }
    }

    pub fn as_ptr(&mut self) -> &mut chromium::cef::cef_app_t {
        &mut self.cef
    }

    fn cef_app() -> chromium::cef::cef_app_t {
        unsafe extern "C" fn get_render_process_handler(
            self_: *mut chromium::cef::_cef_app_t,
        ) -> *mut chromium::cef::_cef_render_process_handler_t {
            let a = self_ as *mut App;
            (*a).render_process_handler.as_ptr()
        }

        unsafe extern "C" fn on_before_command_line_processing(
            _self: *mut chromium::cef::_cef_app_t,
            _process_type: *const chromium::cef::cef_string_t,
            command_line: *mut chromium::cef::_cef_command_line_t,
        ) {
            let disable_component_update = cef_string("disable-component-update");
            ((*command_line).append_switch.unwrap())(command_line, &disable_component_update)
        }

        chromium::cef::cef_app_t {
            base: new_cef_base_ref_counted(mem::size_of::<chromium::cef::cef_app_t>()),
            on_before_command_line_processing: Option::Some(on_before_command_line_processing),
            on_register_custom_schemes: Option::None,
            get_resource_bundle_handler: Option::None,
            get_browser_process_handler: Option::None,
            get_render_process_handler: Option::Some(get_render_process_handler),
        }
    }
}

#[repr(C)]
struct RenderProcessHandler {
    cef: chromium::cef::_cef_render_process_handler_t,
    function_handler: Option<V8Handler>,
    context: *mut chromium::cef::cef_v8context_t,
    functions: Vec<(c_int, chromium::cef::cef_string_userfree_t)>,
}

impl RenderProcessHandler {
    fn new() -> RenderProcessHandler {
        RenderProcessHandler {
            cef: RenderProcessHandler::cef_render_process_handler(),
            function_handler: Option::None,
            context: ::std::ptr::null_mut(),
            functions: Vec::new(),
        }
    }

    pub fn as_ptr(&mut self) -> &mut chromium::cef::_cef_render_process_handler_t {
        &mut self.cef
    }

    fn cef_render_process_handler() -> chromium::cef::_cef_render_process_handler_t {
        unsafe extern "C" fn on_context_created(
            self_: *mut chromium::cef::_cef_render_process_handler_t,
            browser: *mut chromium::cef::_cef_browser_t,
            frame: *mut chromium::cef::_cef_frame_t,
            context: *mut chromium::cef::_cef_v8context_t,
        ) {
            if (*frame).is_main.unwrap()(frame) == 1 {
                let rph = self_ as *mut RenderProcessHandler;
                (*rph).function_handler = Option::Some(V8Handler::new(browser));
                let handler: Option<&mut V8Handler> = (*rph).function_handler.as_mut();
                let handler: &mut V8Handler = handler.expect("no handler");
                (*rph).context = context;
                // Retrieve the context's window object.
                let global = (*context).get_global.unwrap()(context);

                let pendings = &mut (*rph).functions;
                for pending in pendings.iter() {
                    let (id, name) = pending;
                    register_function(*id, *name, global, handler);
                }
            }
        }

        unsafe extern "C" fn on_process_message_received(
            self_: *mut chromium::cef::_cef_render_process_handler_t,
            browser: *mut chromium::cef::_cef_browser_t,
            _frame: *mut chromium::cef::_cef_frame_t,
            source_process: chromium::cef::cef_process_id_t,
            message: *mut chromium::cef::_cef_process_message_t,
        ) -> c_int {
            if source_process == chromium::cef::cef_process_id_t::PID_BROWSER {
                let valid = (*message).is_valid.unwrap()(message);
                let name = (*message).get_name.unwrap()(message);
                if valid == 0 {
                    return 0;
                }
                let rph = self_ as *mut RenderProcessHandler;
                let handled = if chromium::cef::cef_string_utf16_cmp(
                    &chromium::utils::cef_string("eval"),
                    name,
                ) == 0
                {
                    handle_eval(browser, message);
                    1
                } else if chromium::cef::cef_string_utf16_cmp(
                    &chromium::utils::cef_string("function"),
                    name,
                ) == 0
                {
                    let args = (*message).get_argument_list.unwrap()(message);
                    let id = (*args).get_int.unwrap()(args, 0);
                    let name = (*args).get_string.unwrap()(args, 1);

                    let context = (*rph).context;
                    (*rph).functions.push((id, name));
                    if !(*rph).context.is_null() {
                        let handler: Option<&mut V8Handler> = (*rph).function_handler.as_mut();
                        let handler: &mut V8Handler = handler.expect("no handler");

                        let s = (*context).enter.unwrap()(context);
                        assert_eq!(s, 1);
                        let global = (*context).get_global.unwrap()(context);

                        register_function(id, name, global, handler);
                        let s = (*context).exit.unwrap()(context);
                        assert_eq!(s, 1);
                    }
                    1
                } else {
                    0
                };

                chromium::cef::cef_string_userfree_utf16_free(name);
                return handled;
            }
            0
        }

        chromium::cef::_cef_render_process_handler_t {
            base: new_cef_base_ref_counted(mem::size_of::<
                chromium::cef::_cef_render_process_handler_t,
            >()),
            on_web_kit_initialized: Option::None,
            on_browser_created: Option::None,
            on_browser_destroyed: Option::None,
            get_load_handler: Option::None,
            on_context_created: Option::Some(on_context_created),
            on_context_released: Option::None,
            on_uncaught_exception: Option::None,
            on_focused_node_changed: Option::None,
            on_process_message_received: Option::Some(on_process_message_received),
        }
    }
}

fn register_function(
    id: c_int,
    name: *mut chromium::cef::cef_string_t,
    global: *mut chromium::cef::cef_v8value_t,
    handler: &mut V8Handler,
) {
    // Add the "myfunc" function to the "window" object.
    let handler_name = chromium::utils::cef_string(&format!("{id}"));
    let func =
        unsafe { chromium::cef::cef_v8value_create_function(&handler_name, handler.as_ptr()) };
    let s = unsafe {
        (*global).set_value_bykey.unwrap()(
            global,
            name,
            func,
            chromium::cef::cef_v8_propertyattribute_t::V8_PROPERTY_ATTRIBUTE_NONE,
        )
    };
    assert_eq!(s, 1);
}

unsafe fn handle_eval(
    browser: *mut chromium::cef::_cef_browser_t,
    message: *mut chromium::cef::_cef_process_message_t,
) {
    let args = (*message).get_argument_list.unwrap()(message);
    let port = (*args).get_int.unwrap()(args, 0) as u16;
    let id = (*args).get_int.unwrap()(args, 1);
    let code = (*args).get_string.unwrap()(args, 2);

    let frame = (*browser).get_main_frame.unwrap()(browser);
    let context = (*frame).get_v8context.unwrap()(frame);
    let url_cef = chromium::utils::cef_string("http://text/");
    let mut ret = ::std::ptr::null_mut();
    let mut ex = ::std::ptr::null_mut();

    let s = (*context).eval.unwrap()(context, code, &url_cef, 1, &mut ret, &mut ex);
    if s == 0 {
        let ret_str_cef = (*ex).get_message.unwrap()(ex);
        let ret_str = chromium::utils::cstr_from_cef(ret_str_cef);
        let ret_str = CStr::from_ptr(ret_str);
        socket::socket_client(port, ret_str.to_owned(), socket::ReturnType::Error);
        chromium::cef::cef_string_userfree_utf16_free(ret_str_cef);
    } else {
        let (ret_str, kind) = convert_type(ret, id, context);
        socket::socket_client(port, ret_str, kind);
    }
}

unsafe fn convert_type(
    ret: *mut chromium::cef::cef_v8value_t,
    _eval_id: c_int,
    context: *mut chromium::cef::cef_v8context_t,
) -> (CString, socket::ReturnType) {
    if (*ret).is_null.expect("is_null")(ret) == 1 || (*ret).is_undefined.unwrap()(ret) == 1 {
        let ret_str = CString::new("").unwrap();
        (ret_str, socket::ReturnType::Null)
    } else if (*ret).is_bool.unwrap()(ret) == 1 {
        let ret_cef = (*ret).get_bool_value.unwrap()(ret);
        let ret_str = CString::new(format!("{ret_cef}")).unwrap();
        (ret_str, socket::ReturnType::Bool)
    } else if (*ret).is_int.unwrap()(ret) == 1 {
        let ret_cef = (*ret).get_int_value.unwrap()(ret);
        let ret_str = CString::new(format!("{ret_cef}")).unwrap();
        (ret_str, socket::ReturnType::Double)
    } else if (*ret).is_uint.unwrap()(ret) == 1 {
        let ret_cef = (*ret).get_uint_value.unwrap()(ret);
        let ret_str = CString::new(format!("{ret_cef}")).unwrap();
        (ret_str, socket::ReturnType::Double)
    } else if (*ret).is_double.unwrap()(ret) == 1 {
        let ret_cef = (*ret).get_double_value.unwrap()(ret);
        let ret_str = CString::new(format!("{ret_cef}")).unwrap();
        (ret_str, socket::ReturnType::Double)
    } else if (*ret).is_string.unwrap()(ret) == 1 {
        let ret_str_cef = (*ret).get_string_value.unwrap()(ret);
        let ret_str = chromium::utils::cstr_from_cef(ret_str_cef);
        let cstr = chromium::utils::str_from_c(ret_str);
        (
            CString::new(cstr).expect("Failed to convert v8string to CString"),
            socket::ReturnType::Str,
        )
    } else if (*ret).is_array.unwrap()(ret) == 1 {
        let length = (*ret).get_array_length.unwrap()(ret);
        let mut arraystr = String::new();
        let array_val = ret;
        if !context.is_null() {
            let s = (*context).enter.unwrap()(context);
            assert_eq!(s, 1);
        }

        arraystr.push('"');
        for i in 0..length {
            let vali = (*array_val).get_value_byindex.unwrap()(array_val, i);
            let (valcstr, valtyp) = convert_type(vali, _eval_id, context);
            let typ = valtyp as u32;
            let valstr = valcstr.into_string().unwrap();
            let valstr = format!("'{typ},{valstr}'");
            if i > 0 {
                arraystr.push(';');
            }
            arraystr.push_str(&valstr);
        }
        arraystr.push('"');
        if !context.is_null() {
            let s = (*context).exit.unwrap()(context);
            assert_eq!(s, 1);
        }
        (CString::new(arraystr).unwrap(), socket::ReturnType::Array)
    } else {
        let ret_str = CString::new("51").unwrap();
        (ret_str, socket::ReturnType::Error)
    }
}

#[repr(C)]
struct V8Handler {
    cef: chromium::cef::_cef_v8handler_t,
    browser: *mut chromium::cef::_cef_browser_t,
}

impl V8Handler {
    fn new(browser: *mut chromium::cef::_cef_browser_t) -> V8Handler {
        V8Handler {
            cef: V8Handler::cef_function_handler(),
            browser,
        }
    }

    pub fn as_ptr(&mut self) -> &mut chromium::cef::_cef_v8handler_t {
        &mut self.cef
    }

    fn cef_function_handler() -> chromium::cef::_cef_v8handler_t {
        unsafe extern "C" fn execute(
            self_: *mut chromium::cef::_cef_v8handler_t,
            name: *const chromium::cef::cef_string_t,
            _object: *mut chromium::cef::_cef_v8value_t,
            arguments_count: usize,
            arguments: *const *mut chromium::cef::_cef_v8value_t,
            retval: *mut *mut chromium::cef::_cef_v8value_t,
            exception: *mut chromium::cef::cef_string_t,
        ) -> c_int {
            let handler = self_ as *mut V8Handler;
            let browser = (*handler).browser;

            let msg_name = chromium::utils::cef_string("function_call");
            let msg = chromium::cef::cef_process_message_create(&msg_name);
            let args = (*msg).get_argument_list.unwrap()(msg);
            let nm = chromium::utils::str_from_c(chromium::utils::cstr_from_cef(name));
            let s =
                (*args).set_int.unwrap()(args, 1, nm.parse::<i32>().expect("failed to parse i32"));
            assert_eq!(s, 1);

            for i in 0..arguments_count {
                let v8val = arguments.wrapping_add(i);

                if !v8val.is_null() {
                    let ptr = v8val.read();
                    let (cstr, kind) = convert_type(ptr, 0, ::std::ptr::null_mut());
                    let s = (*args).set_int.unwrap()(args, 1 + i * 2 + 1, kind as i32);
                    assert_eq!(s, 1);
                    let rstr = cstr.into_string().expect("failed to convert string");
                    let strval = chromium::utils::cef_string(&rstr);
                    let s = (*args).set_string.unwrap()(args, 1 + i * 2 + 2, &strval);
                    assert_eq!(s, 1);
                }
            }

            let result = socket::wait_response(
                browser,
                msg,
                args,
                chromium::cef::cef_process_id_t::PID_BROWSER,
                None,
            );
            match result {
                Ok(return_st) => {
                    match map_type(return_st.kind, return_st.str_value.to_str().unwrap()) {
                        Ok(v) => {
                            *retval = v;
                        }
                        Err(e) => {
                            *exception = chromium::utils::cef_string(e);
                        }
                    }
                }
                Err(_e) => {
                    *exception = chromium::utils::cef_string("socket server panic");
                }
            };
            1
        }

        chromium::cef::_cef_v8handler_t {
            base: new_cef_base_ref_counted(mem::size_of::<chromium::cef::_cef_v8handler_t>()),
            execute: Option::Some(execute),
        }
    }
}

unsafe fn map_type(
    kind: socket::ReturnType,
    str_value: &str,
) -> Result<*mut chromium::cef::cef_v8value_t, &str> {
    match kind {
        socket::ReturnType::Null => Ok(chromium::cef::cef_v8value_create_null()),
        socket::ReturnType::Bool => {
            let boolean = str_value.parse::<i32>().expect("cannot parse i32");
            Ok(chromium::cef::cef_v8value_create_bool(boolean))
        }
        socket::ReturnType::Double => {
            let double = str_value.parse::<f64>().expect("cannot parse f64");
            Ok(chromium::cef::cef_v8value_create_double(double))
        }
        socket::ReturnType::Str => {
            let str_cef = chromium::utils::cef_string(str_value);
            Ok(chromium::cef::cef_v8value_create_string(&str_cef))
        }
        socket::ReturnType::Array => {
            let rstr = str_value;
            let rstr = rstr.get(1..rstr.len() - 1).expect("not quoted");
            let v = split(rstr, '"', ';');
            let array = chromium::cef::cef_v8value_create_array(v.len() as i32);
            for (i, str) in v.iter().enumerate() {
                let elem_unquoted = str.get(1..str.len() - 1).expect("elem not quoted");
                let parts = splitn(elem_unquoted, 2, '\'', ',');
                let elem_type = socket::ReturnType::from(parts[0].parse::<i32>().unwrap());
                let elem_value = map_type(elem_type, parts[1]);
                let s = (*array).set_value_byindex.unwrap()(
                    array,
                    i as i32,
                    elem_value.expect("invalid elem type"),
                );
                assert_eq!(s, 1, "failed to set v8array index");
            }
            Ok(array)
        }
        _ => Err(str_value),
    }
}

fn split(rstr: &'_ str, quote: char, sep: char) -> Vec<&'_ str> {
    let mut in_string = false;
    let v: Vec<&str> = rstr
        .split_terminator(|c| {
            if c == quote && in_string {
                in_string = false;
            } else if c == quote && !in_string {
                in_string = true;
            }
            c == sep && !in_string
        })
        .collect();
    v
}

fn splitn(rstr: &'_ str, max: usize, quote: char, sep: char) -> Vec<&'_ str> {
    let mut in_string = false;
    let v: Vec<&str> = rstr
        .splitn(max, |c| {
            if c == quote && in_string {
                in_string = false;
            } else if c == quote && !in_string {
                in_string = true;
            }
            c == sep && !in_string
        })
        .collect();
    v
}
