/*
 * Copyright (c) 2019: Victor Toni
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied:
 *     GNU General Public License, version 2 with the GNU Classpath Exception
 * which is available at
 *     https://www.gnu.org/software/classpath/license.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 *
 * Contributors:
 *     Victor Toni - initial implementation
 */
package org.kromo.lambdabus.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.contract.LambdaBusContract;
import org.kromo.lambdabus.dispatcher.EventDispatcher;

/**
 * Extends the behavioral contract of the {@link LambdaBus} by extending
 * {@link DispatchingLambdaBus} with custom tests. This class should be extended
 * for tests of subclasses of {@link EventDispatcher} used within
 * {@link DispatchingLambdaBus}.
 *
 * @author Victor Toni - initial implementation
 *
 */
public abstract class AbstractDispatchingLambdaBusContract
        extends LambdaBusContract<DispatchingLambdaBus> {

    @ParameterizedTest(name = "{1}: NullEventRunnable gets called on null event")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("NullEventRunnable gets called on null event")
    public void nullEventRunnableGetsCalledOnNullEvent(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        final Function<CountDownLatch, Object> nullEventProvider = (doneLatch) -> null;
        final AtomicInteger atomicCounter = new AtomicInteger();
        try (final DispatchingLambdaBus lb = createLambdaBus()) {

            lb.setRunnableForNullEvent(atomicCounter::incrementAndGet);

            for (int i = 0; i < TINY_EVENT_COUNT; i++) {
                assertEquals(i, atomicCounter.get());
                createEventPostAndWaitForDispatchingToComplete(
                        lb,
                        postMethod,
                        0,
                        nullEventProvider);
                assertEquals(i + 1, atomicCounter.get());
            }
        }
    }

    @ParameterizedTest(name = "{1}: NullEventRunnable gets not called on non-null event")
    @MethodSource("getPostMethodsWithNames")
    @DisplayName("NullEventRunnable gets not called on non-null event")
    public void nullEventRunnableGetsNotCalledOnNonNullEvent(
            final BiConsumer<LambdaBus, Object> postMethod,
            final String nameOnlyUsedForUnitTestName) {
        final AtomicInteger atomicCounter = new AtomicInteger();
        try (final DispatchingLambdaBus lb = createLambdaBus()) {

            lb.setRunnableForNullEvent(atomicCounter::incrementAndGet);

            for (int i = 0; i < TINY_EVENT_COUNT; i++) {
                assertEquals(0, atomicCounter.get());
                createEventPostAndWaitForDispatchingToComplete(
                        lb,
                        postMethod,
                        0,
                        A::new);
                assertEquals(0, atomicCounter.get());
            }
        }
    }

    @Test
    @DisplayName("Exceptions from RunnableForNullEvent are not propagated")
    public void exceptionsFromRunnableForNullEventAreNotPropagated() {
        try (final DispatchingLambdaBus lb = createLambdaBus()) {
            assertTrue(lb.hasRunnableForNullEvent(), "Should have a Runnable for null events");

            lb.unsetRunnableForNullEvent();
            assertFalse(lb.hasRunnableForNullEvent(), "Must not have a Runnable for null events");

            final CountDownLatch calledLatch = new CountDownLatch(1);
            final Runnable exceptionThrowingNullEventRunnable = () -> {
                try {
                    throw new RuntimeException(
                            "Can be ignored. Just testing NullEventRunnable throwing Exceptions");
                } finally {
                    calledLatch.countDown();
                }
            };

            lb.setRunnableForNullEvent(exceptionThrowingNullEventRunnable);
            assertTrue(lb.hasRunnableForNullEvent(), "Must have a Runnable for null events");

            // expected that the exception thrown by the NullEventRunnable is not propagated
            lb.post(null);

            assertTimeoutPreemptively(
                    DEFAULT_TIMEOUT,
                    (Executable) calledLatch::await);

            lb.unsetRunnableForNullEvent();
            assertFalse(lb.hasRunnableForNullEvent(), "Must not have a Runnable for null events");
        }
    }

}
