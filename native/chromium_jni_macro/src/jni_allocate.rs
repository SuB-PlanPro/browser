/**
 * Copyright (c) 2022 DB Netz AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
extern crate proc_macro;

use proc_macro::TokenStream;
use proc_macro2::{Ident, Span};
use quote::quote;
use syn::{
    parse_macro_input, punctuated::Punctuated, token::Comma, Data, DataStruct, DeriveInput,
    Field, Fields, Type,
};
use crate::utils::jni_utils::{ extract_arguments, extract_result, jni_signature};

/// Implementation for derive(JNICEFCallback)
///
/// which derives jni_allocate(env, JObject)
/// 
/// Creates an implementation which allocates a CEF callback structure with extra memory
/// to save extra JNI fields. It then fills the CEF structure's callbacks with functions 
/// which call the corresponding Java class if a class member function of the correct name
/// and signature exists.
/// 
/// IMPROVE: Extra validation. This only works for CEF classes which
/// 1) contain a cef_base_ref_counted_t base member
/// 2) only contain callbacks (Option<function pointer>)
/// 
pub fn jni_allocate(tokens: TokenStream) -> TokenStream {
    let input = parse_macro_input!(tokens as DeriveInput);
    let name = input.ident;

    // Collect named fields of the struct
    let fields = match input.data {
        Data::Struct(DataStruct {
            fields: Fields::Named(fields),
            ..
        }) => fields.named,
        _ => panic!("only named fields are allowed"),
    };

    // For each field, build the native callback which calls into Java
    let jni_callbacks = build_jni_callback(&fields, name.clone());

    // For each field, build the field initialization for the CEF type
    let fields = build_initialization(&fields);

    let modified = quote! {
        impl JNICEFCallback for #name {
            fn jni_allocate(env: JNIEnv, object: GlobalRef) -> chromium_jni_utils::JNIWrapperType<#name> {
                let class = env.get_object_class(object.as_obj()).unwrap();
                
                #(#jni_callbacks)*

                return chromium_jni_utils::JNIWrapperType {
                    value: #name {
                        base: _cef_base_ref_counted_t {
                            size: std::mem::size_of::<JNIWrapperType<#name>>(),
                            add_ref: None,
                            release: None,
                            has_one_ref: None,
                            has_at_least_one_ref: None,
                        }
                        #(#fields, )*
                    },
                    this: object,
                    jvm: env.get_java_vm().unwrap()
                };
            }
        }
    };
    
    TokenStream::from(modified)
}

/// For a field `x` construct an initialization which looks up the function via JNI
fn build_initialization(
    fields: &Punctuated<Field, Comma>,
) -> impl Iterator<Item = proc_macro2::TokenStream> + '_ {
    let fields = fields.iter().enumerate().map(move |(_i, field)| {
        // Skip first field, as base is manually initialized
        if _i == 0 {
            return quote! {};
        }

        let field_ident = field.ident.as_ref().unwrap();
        let func_ident = syn::Ident::new(&format!("jni_{field_ident}"), Span::call_site());
        let signature = jni_signature(field);
        
        quote! { #field_ident: match env.get_method_id(class, stringify!(#field_ident), #signature) {
                Err(e) => {
                    // Attempting to get the method id of a method that does not exists results 
                    // in a NoSuchMethodException. As we do not want to throw this, clear the exception 
                    env.exception_clear().unwrap();
                    None
                },
                Ok(_) => Some(#func_ident)
            }
        }
    });
    fields
}

/// for a field of type Option<T> create a function which calles the corresponding Java function via JNI
fn build_jni_callback(
    fields: &Punctuated<Field, Comma>,
    _name: Ident,
) -> impl Iterator<Item = proc_macro2::TokenStream> + '_ {
    let fields = fields.iter().enumerate().map(move |(_i, field)| {
        if _i == 0 {
            return quote! {};
        }

        let field_ident = field.ident.as_ref().unwrap();
        let field_type = &field.ty;
        let args = match field_type {
           Type::Path(p) => extract_arguments(&p.path),
           &_ => panic!("other")
        };
        let result = match field_type {
           Type::Path(p) => extract_result(&p.path),
           &_ => panic!("other")
        };

        let result_tag =  match &result{
            None => quote! {},
            Some(t) => quote!{ -> #t }
        };

        let argnames = args.iter().map(|arg| {
            match &arg.name {
                Some((name, _)) => quote! { ToJava::to_java(env, #name) },
                None => panic!("unnamed argument")
            }
        });

        let retline = match result {
            None => quote! {},
            Some(_) => quote! { return chromium_jni_utils::FromJavaValue::from_java_value(env, result); }
        };
        
        let func_ident = syn::Ident::new(&format!("jni_{field_ident}"), Span::call_site());
        
        let signature = jni_signature(field);
        quote! {
            /// C-Callback which calls the Java function via JNI
            unsafe extern "C" fn #func_ident(#args) #result_tag {
                let wrapper = jni_unwrap(self_);
                let guard = (*wrapper).jvm.attach_current_thread().unwrap();
                let env: JNIEnv = *guard;
                let result = env.call_method((*wrapper).this.as_obj(), stringify!(#field_ident), #signature, &[#(#argnames, )*]).expect(stringify!(#func_ident));
                #retline
            };
        }
    });
    fields
}
