/**
 * Copyright (c) 2022 DB Netz AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
extern crate proc_macro;
use crate::jni_name::{create_jni_fn_name, JNIName};
use crate::utils::unzip4::Unzip4;
use proc_macro::TokenStream;
use proc_macro2::{Ident, Span};
use quote::quote;
use syn::{parse_macro_input, FnArg, Type};

pub fn jni_wrap_attr_impl(attr: TokenStream, item: TokenStream) -> TokenStream {
    let JNIName { namespace, suffix } = parse_macro_input!(attr as JNIName);
    let item2 = item.clone().into();
    let function: syn::ItemFn =
        syn::parse2(item2).expect("jni_wrap can only be applied to a function");

    let (func_args, param_prepare, call_args, param_cleanup): (
        Vec<proc_macro2::TokenStream>,
        Vec<proc_macro2::TokenStream>,
        Vec<proc_macro2::TokenStream>,
        Vec<proc_macro2::TokenStream>,
    ) = function
        .sig
        .inputs
        .iter()
        .enumerate()
        .map(|(i, ty)| setup_argument(i, ty))
        .unzip4();

    let func_name = &function.sig.ident;
    // Return type
    let return_type = match &function.sig.output {
        syn::ReturnType::Type(_, ty) => Option::Some(quote!(#ty).to_string()),
        _ => Option::None,
    };

    // Call expression: either a simple function call, or one which collects the result
    let call_expr = match &return_type {
        None => quote! { #func_name(#(#call_args, )*); },
        Some(_) => quote! { let result = #func_name(#(#call_args, )*); },
    };

    // Return expression: Return the result if present
    let (ret_expr, return_expr) = handle_return(&return_type);

    let original_function: proc_macro2::TokenStream = item.into();
    let fname = syn::Ident::new(
        &create_jni_fn_name(&namespace, &function.sig.ident.to_string(), &suffix),
        function.sig.ident.span(),
    );
    let q = quote! {
        #[no_mangle]
        pub extern fn #fname(_env: jni::JNIEnv, _class: jni::objects::JClass, #(#func_args, )*) #ret_expr {
            #(#param_prepare; )*
            #call_expr
            #(#param_cleanup; )*
            #return_expr
        }

        #original_function
    };
    TokenStream::from(q)
}

/// Create per-argument statements as a 4-tuple
/// 1: Function argument
/// 2: Mapping statement (Java type -> Rust type)
/// 3: Call expression
/// 4: Cleanup statement (for manually cleared types created during 2)
fn setup_argument(
    index: usize,
    ty: &FnArg,
) -> (
    proc_macro2::TokenStream,
    proc_macro2::TokenStream,
    proc_macro2::TokenStream,
    proc_macro2::TokenStream,
) {
    let ty = match ty {
        FnArg::Typed(pattern) => &pattern.ty,
        FnArg::Receiver(_) => todo!(),
    };
    let arg = Ident::new(&format!("arg{index}"), Span::call_site());
    let param = Ident::new(&format!("param{index}"), Span::call_site());
    let (arg_type, prepare, cleanup) = handle_arg(&arg, &param, ty);

    (
        // JNI Function argument: arg[index]: type
        quote! { #arg : #arg_type },
        // Preparation statement: let param[index] = arg[index] + conversion
        quote! { let #param = #prepare },
        // Call expression
        quote! { #param },
        // Cleanup statement as required by the preparation statement
        cleanup,
    )
}

/// Determines result type and mapping to the Java equivalent
/// result is (resultType, mapping)
fn handle_return(retn: &Option<String>) -> (proc_macro2::TokenStream, proc_macro2::TokenStream) {
    match retn {
        None => (quote! {}, quote! {}),
        Some(t) => match t.as_str() {
            "String" => (
                quote! { -> jni::sys::jstring },
                quote! { return _env.new_string(result).unwrap().into_inner(); },
            ),
            "CStr" => (
                quote! { -> jni::sys::jstring },
                quote! { return _env.new_string(result.to_str().unwrap()).unwrap().into_inner(); },
            ),
            "* mut c_char" | "* const c_char" => (
                quote! { -> jni::sys::jstring },
                quote! {
                    if result.is_null() {
                        return std::ptr::null_mut();
                    }
                    let result = unsafe { CStr::from_ptr(result) };
                    return _env.new_string(result.to_str().unwrap()).unwrap().into_inner();
                },
            ),
            "cef :: cef_string_userfree_t" => (
                quote! { -> jni::sys::jstring },
                quote! {
                    let str: String = chromium::utils::str_from_cef(result);
                    unsafe { cef::cef_string_userfree_utf16_free(result) };
                    return _env.new_string(str).unwrap().into_inner();
                },
            ),
            "c_int" | "i32" => (
                quote! { -> jni::sys::jint },
                quote! { return result as jni::sys::jint; },
            ),
            _ => {
                // Catch all for pointers
                if t.starts_with("* mut ") || t.starts_with("* const ") {
                    (
                        quote! { -> jni::sys::jlong },
                        quote! { return result as jni::sys::jlong },
                    )
                } else {
                    panic!("Invalid return type {retn:?}");
                }
            }
        },
    }
}

/// Determines parameter type, mapping from the Java equivalent and cleanup for temporaries
/// result is (paramType, mapping, cleanup)
fn handle_arg(
    arg: &Ident,
    param: &Ident,
    ty: &Type,
) -> (
    proc_macro2::TokenStream,
    proc_macro2::TokenStream,
    proc_macro2::TokenStream,
) {
    let tystr = quote! {#ty}.to_string();
    match tystr.as_str() {
        "i32" | "c_int" => (
            quote! { jni::sys::jint },
            quote! { #arg.try_into().unwrap() },
            quote! {},
        ),
        "i64" | "usize" => (
            quote! { jni::sys::jlong },
            quote! { #arg.try_into().unwrap() },
            quote! {},
        ),
        "f64" => (
            quote! { jni::sys::jdouble },
            quote! { #arg.try_into().unwrap() },
            quote! {},
        ),
        "bool" => (
            quote! { jni::sys::jboolean },
            quote! { #arg != 0 },
            quote! {},
        ),
        "* const c_char" => (
            quote! { jni::objects::JString },
            quote! { _env.get_string_utf_chars(#arg).unwrap_or(std::ptr::null_mut()) },
            quote! { if !#param.is_null() {
                _env.release_string_utf_chars(#arg, #param).unwrap()
            }},
        ),
        "Option < Vec < u8 > >" => (
            quote! { jni::sys::jbyteArray },
            quote! { _env.convert_byte_array(#arg).map(|arr| Some(arr)).unwrap_or(None) },
            quote! {},
        ),
        "Vec < u8 >" => (
            quote! { jni::sys::jbyteArray },
            quote! { _env.convert_byte_array(#arg).unwrap() },
            quote! {},
        ),
        _ => {
            // Catch all for other pointers and refs
            if tystr.starts_with("* mut ")
                || tystr.starts_with("& mut ")
                || tystr.starts_with("unsafe extern \"system\" fn")
            {
                (quote! { #ty }, quote! { #arg }, quote! {})
            } else {
                panic!("Invalid argument type {tystr:?}");
            }
        }
    }
}
