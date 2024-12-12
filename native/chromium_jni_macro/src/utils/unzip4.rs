/**
 * Copyright (c) 2022 DB Netz AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
/// Trait for unzipping 4-tuples
pub trait Unzip4<A, B, C, D> {
    fn unzip4<FromA, FromB, FromC, FromD>(self) -> (FromA, FromB, FromC, FromD)
    where
        FromA: Default + Extend<A>,
        FromB: Default + Extend<B>,
        FromC: Default + Extend<C>,
        FromD: Default + Extend<D>;
}

impl<I, A, B, C, D> Unzip4<A, B, C, D> for I
where
    I: Sized + Iterator<Item = (A, B, C, D)>,
{
    fn unzip4<FromA, FromB, FromC, FromD>(self) -> (FromA, FromB, FromC, FromD)
    where
        FromA: Default + Extend<A>,
        FromB: Default + Extend<B>,
        FromC: Default + Extend<C>,
        FromD: Default + Extend<D>,
    {
        struct SizeHint<A>(usize, Option<usize>, std::marker::PhantomData<A>);
        impl<A> Iterator for SizeHint<A> {
            type Item = A;

            fn next(&mut self) -> Option<A> {
                None
            }
            fn size_hint(&self) -> (usize, Option<usize>) {
                (self.0, self.1)
            }
        }

        let (lo, hi) = self.size_hint();
        let mut ts: FromA = Default::default();
        let mut us: FromB = Default::default();
        let mut vs: FromC = Default::default();
        let mut ws: FromD = Default::default();

        ts.extend(SizeHint(lo, hi, std::marker::PhantomData));
        us.extend(SizeHint(lo, hi, std::marker::PhantomData));
        vs.extend(SizeHint(lo, hi, std::marker::PhantomData));
        ws.extend(SizeHint(lo, hi, std::marker::PhantomData));

        for (t, u, v, w) in self {
            ts.extend(Some(t));
            us.extend(Some(u));
            vs.extend(Some(v));
            ws.extend(Some(w));
        }

        (ts, us, vs, ws)
    }
}
