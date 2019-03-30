/*******************************************************************************
 * Copyright (c) 2018: Victor Toni
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
 *******************************************************************************/
package org.kromo.lambdabus.impl.opt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.impl.concurrent.NonTerminatingExecutorService;
import org.kromo.lambdabus.util.DispatchingUtil;

/**
 * Tests the abstract {@link AbstractThreadedLambdaBus} class by using a simple
 * sub-class to instantiate it.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class AbstractThreadedLambdaBusTest {

    private static final int ZERO = 0;
    private static final int ONE = 1;

    protected static final int DEFAULT_TIMEOUT_MILLIS = 500;

    @ParameterizedTest(name = "Constructor - default ThreadingMode.{0} and EnumSet.of(ThreadingMode.{0})")
    @EnumSource(ThreadingMode.class)
    @DisplayName("Constructor - default ThreadingMode and EnumSet.of(ThreadingMode)")
    public void constructor(
            final ThreadingMode defaultThreadingMode
    ) {
        final Set<ThreadingMode> supportedThreadingModes = EnumSet.of(defaultThreadingMode);

        final ExecutorService executorService = Mockito.mock(ExecutorService.class);

        assertDoesNotThrow(
            () -> {
                final AbstractThreadedLambdaBus lb = new TestingThreadedLambdaBus(
                        defaultThreadingMode,
                        supportedThreadingModes,
                        executorService);
                try {
                    assertFalse(lb.isClosed(), "LambdaBus must not be closed yet");
                } finally {
                    lb.close();
                }

                assertTrue(lb.isClosed(), "LambdaBus must be closed");
            }
        );
    }

    @ParameterizedTest(name = "Constructor - Unsupported default ThreadingMode.{0} and EnumSet.complementOf(ThreadingMode.{0})")
    @EnumSource(ThreadingMode.class)
    @DisplayName("Constructor - Unsupported defaultThreadingMode throws IllegalArgumentException")
    public void constructorUnsupportedDefaultThreadingModeThrowsIllegalArgumentException(
            final ThreadingMode unsupportedThreadingMode
    ) {
        final Set<ThreadingMode> complementingThreadingModes = EnumSet.allOf(ThreadingMode.class);
        complementingThreadingModes.remove(unsupportedThreadingMode);

        final ExecutorService executorService = Mockito.mock(ExecutorService.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (final AbstractThreadedLambdaBus lb = new TestingThreadedLambdaBus(
                            unsupportedThreadingMode,
                            complementingThreadingModes,
                            executorService)) {}
                }
        );
    }

    @ParameterizedTest(name = "Constructor - Empty supportedThreadingMode throws IllegalArgumentException (default ThreadingMode.{0})")
    @EnumSource(ThreadingMode.class)
    @DisplayName("Constructor - Empty supportedThreadingMode throws IllegalArgumentException")
    public void constructorEmptySupportedThreadingModeThrowsIllegalArgumentException(
            final ThreadingMode defaultThreadingMode
    ) {
        final Set<ThreadingMode> emptySupportedThreadingModes = Collections.emptySet();

        final ExecutorService executorService = Mockito.mock(ExecutorService.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (final AbstractThreadedLambdaBus lb = new TestingThreadedLambdaBus(
                            defaultThreadingMode,
                            emptySupportedThreadingModes,
                            executorService)) {}
                }
        );
    }

    @Test
    @DisplayName("Constructor - Null ThreadingMode throws NullPointerException")
    public void constructorNullThreadingModeThrowsNPE() {
        final ThreadingMode nullThreadingMode = null;
        final Set<ThreadingMode> supportedThreadingModes = EnumSet.allOf(ThreadingMode.class);

        final ExecutorService executorService = Mockito.mock(ExecutorService.class);

        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final AbstractThreadedLambdaBus lb = new TestingThreadedLambdaBus(
                           nullThreadingMode,
                            supportedThreadingModes,
                            executorService)) {}
                }
        );
    }

    @ParameterizedTest(name = "Constructor - supportedThreadingModes containing null throws NullPointerException (default ThreadingMode.{0}, EnumSet.of(ThreadingMode.{0}))")
    @EnumSource(ThreadingMode.class)
    @DisplayName("Constructor - supportedThreadingModes containing null throws NullPointerException")
    public void constructorSupportedThreadingModesContainsNullThrowsNPE(
            final ThreadingMode defaultThreadingMode
    ) {
        final Set<ThreadingMode> supportedThreadingModes = EnumSet.allOf(ThreadingMode.class);

        final Set<ThreadingMode> supportedThreadingModesContainingNull = new HashSet<>(supportedThreadingModes);
        supportedThreadingModesContainingNull.add(null);

        final ExecutorService executorService = Mockito.mock(ExecutorService.class);

        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final AbstractThreadedLambdaBus lb = new TestingThreadedLambdaBus(
                            defaultThreadingMode,
                            supportedThreadingModesContainingNull,
                            executorService)) {}
                }
        );
    }

    @ParameterizedTest(name = "Constructor - null ExecutorService throws NullPointerException (default ThreadingMode.{0}, EnumSet.of(ThreadingMode.{0}))")
    @EnumSource(ThreadingMode.class)
    @DisplayName("Constructor - null ExecutorService throws NullPointerException")
    public void constructorNullExecutorServiceThrowsNPE(
            final ThreadingMode defaultThreadingMode
    ) {
        final Set<ThreadingMode> supportedThreadingModes= EnumSet.allOf(ThreadingMode.class);

        final ExecutorService nullExecutorService = null;

        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final AbstractThreadedLambdaBus lb = new TestingThreadedLambdaBus(
                            defaultThreadingMode,
                            supportedThreadingModes,
                            nullExecutorService)) {}
                }
        );
    }

    @ParameterizedTest(name = "ExecutorService marked as INTERNAL by ExecutorServiceControl gets shutdown on LambdaBus.close() (default ThreadingMode.{0})")
    @EnumSource(ThreadingMode.class)
    @DisplayName("ExecutorService marked as INTERNAL by ExecutorServiceControl gets shutdown on LambdaBus.close()")
    public void internalExecutorServiceGetsShutdownOnClose(
            final ThreadingMode defaultThreadingMode
    ) {
        final Set<ThreadingMode> supportedThreadingModes = EnumSet.allOf(ThreadingMode.class);

        final ExecutorService executorService = createSpyableExecutorService();
        try {
            // wrap it with spy abilities
            final ExecutorService executorServiceSpy = spy(executorService);

            final AbstractThreadedLambdaBus lb = new TestingThreadedLambdaBus(
                    defaultThreadingMode,
                    supportedThreadingModes,
                    executorServiceSpy);
            try {
                assertFalse(lb.isClosed(), "LambdaBus must not be closed yet");
                assertFalse(executorServiceSpy.isShutdown(), "ExecutorService must not be shutdown yet");
                verify(executorServiceSpy, timeout(DEFAULT_TIMEOUT_MILLIS).times(ZERO)).shutdown();
                verify(executorServiceSpy, timeout(DEFAULT_TIMEOUT_MILLIS).times(ZERO)).shutdownNow();
            } finally {
                lb.close();
            }

            assertTrue(lb.isClosed(), "LambdaBus must be closed");
            assertTrue(executorServiceSpy.isShutdown(), "ExecutorService must be shutdown");
            verify(executorServiceSpy, times(ONE)).shutdownNow();
        } finally {
            // close real ExecutorService (fail safe if class does not behave properly)
            executorService.shutdownNow();
        }
    }

    @ParameterizedTest(name = "Non-terminating decorated ExecutorService gets not shutdown on LambdaBus.close() (default ThreadingMode.{0})")
    @EnumSource(ThreadingMode.class)
    @DisplayName("Non-terminating decorated ExecutorService gets not shutdown on LambdaBus.close()")
    public void externalExecutorServiceGetsNotShutdownOnClose(
            final ThreadingMode defaultThreadingMode
    ) {
        final Set<ThreadingMode> supportedThreadingModes = EnumSet.allOf(ThreadingMode.class);

        final ExecutorService executorService = createSpyableExecutorService();
        // wrap ExecutorService with spy abilities
        final ExecutorService executorServiceSpy = spy(executorService);

        final ExecutorService nonTerminatingExecutorService = new NonTerminatingExecutorService(executorServiceSpy);

        assertFalse(executorServiceSpy.isShutdown(), "ExecutorService must not be shutdown");
        assertFalse(nonTerminatingExecutorService.isShutdown(), "Decorated ExecutorService must not be shutdown");
        try {
            final AbstractThreadedLambdaBus lb = new TestingThreadedLambdaBus(
                    defaultThreadingMode,
                    supportedThreadingModes,
                    nonTerminatingExecutorService);
            try {
                assertFalse(lb.isClosed(), "LambdaBus must not be closed yet");

                assertFalse(executorServiceSpy.isShutdown(), "ExecutorService must not be shutdown");
                assertFalse(nonTerminatingExecutorService.isShutdown(), "Decorated ExecutorService must not be shutdown");

                verify(executorServiceSpy, timeout(DEFAULT_TIMEOUT_MILLIS).times(ZERO)).shutdown();
                verify(executorServiceSpy, timeout(DEFAULT_TIMEOUT_MILLIS).times(ZERO)).shutdownNow();
            } finally {
                lb.close();
            }

            assertTrue(lb.isClosed(), "LambdaBus must be closed");

            assertFalse(executorServiceSpy.isShutdown(), "ExecutorService must not be shutdown");
            assertTrue(nonTerminatingExecutorService.isShutdown(), "Decorated ExecutorService must be shutdown");

            verify(executorServiceSpy, timeout(DEFAULT_TIMEOUT_MILLIS).times(ZERO)).shutdown();
            verify(executorServiceSpy, timeout(DEFAULT_TIMEOUT_MILLIS).times(ZERO)).shutdownNow();
        } finally {
            // close real ExecutorService
            executorService.shutdownNow();
        }
    }

    //##########################################################################
    // Helper methods
    //##########################################################################

    private ExecutorService createSpyableExecutorService() {
        final int corePoolSize = 1;
        final int maximumPoolSize = 1;
        final long keepAliveTime = 0L;
        final TimeUnit timeUnit = TimeUnit.MILLISECONDS;
        final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();

        return new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, timeUnit, workQueue);
    }

    //##########################################################################
    // Helper class to instantiate the abstract class to be tested
    //##########################################################################

    private final static class TestingThreadedLambdaBus
        extends AbstractThreadedLambdaBus {

        protected TestingThreadedLambdaBus(
                final ThreadingMode defaultThreadingMode,
                final Set<ThreadingMode> supportedThreadingModes,
                final ExecutorService executorService
        ) {
            super(
                    defaultThreadingMode,
                    supportedThreadingModes,
                    executorService
            );
        }

        @Override
        protected <T> void acceptNonNullEvent(
                final T event,
                final ThreadingMode supportedThreadingMode
        ) {
            assertNotNull(event, "'event' must not be null");

            assertNotNull(supportedThreadingMode, "'supportedThreadingMode' must not be null");

            tryToDispatchNonNullEvent(event, supportedThreadingMode);
        }

        @Override
        protected <T> void dispatchNonNullEventToSubscriber(
                final T event,
                final Collection<Consumer<T>> eventSubscriberCollection,
                final ThreadingMode supportedThreadingMode
        ) {
            assertNotNull(event, "'event' must not be null");

            assertNotNull(eventSubscriberCollection, "'eventSubscriberCollection' must not be null");
            assertFalse(eventSubscriberCollection.isEmpty(), "'eventSubscriberCollection' must not be empty");

            assertNotNull(supportedThreadingMode, "'supportedThreadingMode' must not be null");

            DispatchingUtil.dispatchEventToSubscriber(
                event,
                eventSubscriberCollection);
        }
    }

}
