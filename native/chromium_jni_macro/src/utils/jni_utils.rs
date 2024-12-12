/**
 * Copyright (c) 2022 DB Netz AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
use syn::punctuated::Punctuated;
use syn::token::Comma;
use syn::PathArguments::AngleBracketed;
use syn::{BareFnArg, Field, GenericArgument, Path, ReturnType, Type};

/// Returns the JNI type for a result type
fn jni_result_signature(field: &Field) -> char {
    let field_type = &field.ty;
    let result = match field_type {
        Type::Path(p) => extract_result(&p.path),
        &_ => panic!("other"),
    };
    match result {
        None => 'V',
        Some(t) => jni_type_signature(&t),
    }
}

/// IMPROVE: Handle other types
fn jni_type_signature(ty: &Type) -> char {
    match ty {
        Type::Ptr(_) => 'J',
        Type::Path(_p) => 'I',
        _ => panic!("Unhandled type {:?}", ty),
    }
}

/// Returns the JNI Signature for a callback field
pub fn jni_signature(field: &Field) -> String {
    let field_type = &field.ty;
    let args = match field_type {
        Type::Path(p) => extract_arguments(&p.path),
        &_ => panic!("other"),
    };
    let argsigs = args
        .iter()
        .map(|arg| jni_type_signature(&arg.ty))
        .collect::<String>();

    format!("({}){}", argsigs, jni_result_signature(field))
}

fn get_type_parameter(p: &Path) -> Punctuated<GenericArgument, Comma> {
    match &p.segments.last().unwrap().arguments {
        AngleBracketed(angle_bracket) => angle_bracket.args.clone(),
        _ => panic!("Not a generic type"),
    }
}

/// Extracts Args from an Option function pointer path Option<*mut fn(Args...)>
pub fn extract_arguments(p: &Path) -> syn::punctuated::Punctuated<BareFnArg, Comma> {
    let bracket_args = get_type_parameter(p);
    match bracket_args.first().unwrap() {
        GenericArgument::Type(Type::BareFn(t)) => t.inputs.clone(),
        _ => panic!("Not a type"),
    }
}

/// Extracts the result type from an Option function pointer path Option<*mut fn(...) -> Result>
pub fn extract_result(p: &Path) -> Option<syn::Type> {
    let bracket_args = get_type_parameter(p);
    match bracket_args.first().unwrap() {
        GenericArgument::Type(Type::BareFn(t)) => match t.output.clone() {
            ReturnType::Type(_, ty) => Some(*ty),
            ReturnType::Default => None,
        },
        _ => panic!("Not a type (result)"),
    }
}
