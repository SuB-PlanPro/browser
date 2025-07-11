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
use quote::ToTokens;
use syn::Meta;
use syn::Type::Group;
use syn::Type::Path;
use syn::{
    parse::{Parse, ParseStream},
    parse_macro_input, Result, Token,
};

pub struct JNIName {
    pub namespace: String,
    pub suffix: String,
}

fn get_type_name(ty: syn::Type) -> String {
    match ty {
        Path(tp) => tp.path.segments.last().unwrap().ident.to_string(),
        Group(grp) => get_type_name(*grp.elem),
        _ => panic!("Not a type"),
    }
}

impl Parse for JNIName {
    fn parse(input: ParseStream) -> Result<Self> {
        let namespace = input.parse::<syn::LitStr>()?.value();
        let suffix: String = if input.peek(Token![,]) {
            input.parse::<Token![,]>()?;
            let ty = input.parse::<syn::Type>()?;
            get_type_name(ty)
        } else {
            String::new()
        };

        Ok(JNIName { namespace, suffix })
    }
}

/// Deals exclusively with `proc_macro2::TokenStream` instead of `proc_macro::TokenStream`,
/// allowing it and all interior functionality to be unit tested.
pub fn jni_name(attr: TokenStream, item: TokenStream) -> TokenStream {
    let item2 = proc_macro2::TokenStream::from(item);
    let mut function: syn::ItemFn =
        syn::parse2(item2).expect("jni_name can only be applied to a function");
    let JNIName { namespace, suffix } = parse_macro_input!(attr as JNIName);

    // Rename function to use the JNI required name
    function.sig.ident = syn::Ident::new(
        &create_jni_fn_name(&namespace, &function.sig.ident.to_string(), &suffix),
        function.sig.ident.span(),
    );

    // Add no_mangle attribute
    function.attrs.push(syn::Attribute {
        pound_token: Default::default(),
        style: syn::AttrStyle::Outer,
        bracket_token: Default::default(),
        meta: Meta::Path(syn::parse_str("no_mangle").unwrap()),
    });

    function.into_token_stream().into()
}

// Constructs a JNI function name from full class name, function name and an optional suffix
pub fn create_jni_fn_name(namespace: &str, name: &str, suffix: &str) -> String {
    let namespace = namespace.replace('_', "_1").replace('.', "_");
    let name = name.replace('_', "_1");
    let suffix = suffix.replace('_', "_1");
    format!("Java_{namespace}_{name}{suffix}")
}
