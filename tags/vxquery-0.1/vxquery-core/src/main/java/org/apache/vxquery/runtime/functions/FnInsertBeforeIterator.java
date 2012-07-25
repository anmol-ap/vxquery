/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.vxquery.runtime.functions;

import java.math.BigInteger;

import org.apache.vxquery.context.StaticContext;
import org.apache.vxquery.datamodel.XDMValue;
import org.apache.vxquery.datamodel.atomic.NumericValue;
import org.apache.vxquery.exceptions.SystemException;
import org.apache.vxquery.functions.Function;
import org.apache.vxquery.runtime.CallStackFrame;
import org.apache.vxquery.runtime.LocalRegisterAccessor;
import org.apache.vxquery.runtime.RegisterAllocator;
import org.apache.vxquery.runtime.base.AbstractLazilyEvaluatedFunctionIterator;
import org.apache.vxquery.runtime.base.RuntimeIterator;

public class FnInsertBeforeIterator extends AbstractLazilyEvaluatedFunctionIterator {
    private final LocalRegisterAccessor<BigInteger> insertPoint;
    private final LocalRegisterAccessor<BigInteger> index;
    private final LocalRegisterAccessor<State> state;

    private enum State {
        PRE, IN, POST,
    }

    public FnInsertBeforeIterator(RegisterAllocator rAllocator, Function fn, RuntimeIterator[] arguments,
            StaticContext ctx) {
        super(rAllocator, fn, arguments, ctx);
        insertPoint = new LocalRegisterAccessor<BigInteger>(rAllocator.allocate(1));
        index = new LocalRegisterAccessor<BigInteger>(rAllocator.allocate(1));
        state = new LocalRegisterAccessor<State>(rAllocator.allocate(1));
    }

    @Override
    public void close(CallStackFrame frame) {
        arguments[0].close(frame);
        arguments[2].close(frame);
    }

    @Override
    public Object next(CallStackFrame frame) throws SystemException {
        if (insertPoint.get(frame) == null) {
            NumericValue nVal = (NumericValue) arguments[1].evaluateEagerly(frame);
            BigInteger i = nVal.getIntegerValue();
            if (i.compareTo(BigInteger.ONE) < 0) {
                i = BigInteger.ONE;
            }
            insertPoint.set(frame, i);
        }
        switch (state.get(frame)) {
            case PRE:
                if (index.get(frame).compareTo(insertPoint.get(frame)) < 0) {
                    XDMValue v = (XDMValue) arguments[0].next(frame);
                    if (v != null) {
                        index.set(frame, index.get(frame).add(BigInteger.ONE));
                        return v;
                    }
                }
                state.set(frame, State.IN);

            case IN:
                XDMValue v = (XDMValue) arguments[2].next(frame);
                if (v != null) {
                    return v;
                }
                state.set(frame, State.POST);

            case POST:
                return arguments[0].next(frame);

            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void open(CallStackFrame frame) {
        arguments[0].open(frame);
        arguments[2].open(frame);
        index.set(frame, BigInteger.ONE);
        insertPoint.set(frame, null);
        state.set(frame, State.PRE);
    }
}