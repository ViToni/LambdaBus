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
package org.kromo.lambdabus.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.util.DispatchingUtil;

/**
 * Tests for {@link AbstractLambdaBus}.<br>
 * Since this is an abstract class we use a trivial private implementation (with
 * its own tests) to test the behavior of the abstract class.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class AbstractLambdaBusTest {

    protected static final int ONE = 1;
    protected static final int TWO = 2;

    /**
     * Timeout used to wait for published events to complete
     */
    protected static final int DEFAULT_TIMEOUT_MILLIS = 500;

    @Test
    @DisplayName("Default Constructor")
    public void constructor( ) {
        assertDoesNotThrow(
                (ThrowingSupplier<AbstractLambdaBus>) TestingAbstractLambdaBus::new
        );
    }

    @ParameterizedTest(name = "Constructor - default ThreadingMode.{0} and EnumSet.of(ThreadingMode.{0})")
    @EnumSource(ThreadingMode.class)
    @DisplayName("Constructor - default ThreadingMode and EnumSet.of(ThreadingMode)")
    public void constructor(
            final ThreadingMode defaultThreadingMode
    ) {
        final Set<ThreadingMode> supportedThreadingModes = EnumSet.of(defaultThreadingMode);

        assertDoesNotThrow(
            () -> {
                final AbstractLambdaBus lb = new TestingAbstractLambdaBus(
                        defaultThreadingMode,
                        supportedThreadingModes);
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

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (final AbstractLambdaBus lb = new TestingAbstractLambdaBus(
                            unsupportedThreadingMode,
                            complementingThreadingModes)) {}
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

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (final AbstractLambdaBus lb = new TestingAbstractLambdaBus(
                            defaultThreadingMode,
                            emptySupportedThreadingModes)) {}
                }
        );
    }

    @Test
    @DisplayName("Constructor - Null ThreadingMode throws NullPointerException")
    public void constructorNullThreadingModeThrowsNPE() {
        final ThreadingMode nullThreadingMode = null;
        final Set<ThreadingMode> supportedThreadingModes = EnumSet.allOf(ThreadingMode.class);

        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final AbstractLambdaBus lb = new TestingAbstractLambdaBus(
                           nullThreadingMode,
                            supportedThreadingModes)) {}
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

        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final AbstractLambdaBus lb = new TestingAbstractLambdaBus(
                            defaultThreadingMode,
                            supportedThreadingModesContainingNull)) {}
                }
        );
    }

    @Test
    @DisplayName("Set / unset default RunnableForNullEvent")
    public void setDefaultRunnableForNullEventAndUnsetDefaultRunnableForNullEvent() {
        try (final TestingAbstractLambdaBus lb = new TestingAbstractLambdaBus() ) {
            assertTrue(lb.hasRunnableForNullEvent(), "Should have a Runnable for null events");

            lb.setDefaultRunnableForNullEvent();
            assertTrue(lb.hasRunnableForNullEvent(), "Should still have a Runnable for null events");

            lb.unsetRunnableForNullEvent();
            assertFalse(lb.hasRunnableForNullEvent(), "Must not have a Runnable for null events");

            lb.unsetRunnableForNullEvent();
            assertFalse(lb.hasRunnableForNullEvent(), "Must not have a Runnable for null events");

            lb.setDefaultRunnableForNullEvent();
            assertTrue(lb.hasRunnableForNullEvent(), "Must have a Runnable for null events");

            lb.setDefaultRunnableForNullEvent();
            assertTrue(lb.hasRunnableForNullEvent(), "Must have a Runnable for null events");

            lb.unsetRunnableForNullEvent();
            assertFalse(lb.hasRunnableForNullEvent(), "Must not have a Runnable for null events");

            lb.unsetRunnableForNullEvent();
            assertFalse(lb.hasRunnableForNullEvent(), "Must not have a Runnable for null events");
        }
    }

    @Test
    @DisplayName("Setting null RunnableForNullEvent throws NullPointerException")
    public void settingNullRunnableForNullEventThrows_NPE() {
        try (final AbstractLambdaBus lb = new TestingAbstractLambdaBus()) {

            assertThrows(
                NullPointerException.class,
                () -> lb.setRunnableForNullEvent(null));
        }
    }

    @Test
    @DisplayName("Setting RunnableForNullEvent on closed bus throws IllegalStateException")
    public void settingRunnableForNullEventOnClosedLambdaBusThrowsIllegalStateException() {
        try (final AbstractLambdaBus lb = new TestingAbstractLambdaBus()) {

            lb.close();

            assertThrows(
                IllegalStateException.class,
                () -> lb.setRunnableForNullEvent(System.out::println));
        }
    }

    @ParameterizedTest(name = "Posting with unsupported ThreadingMode reverts to default ThreadingMode.{0}")
    @EnumSource(value=ThreadingMode.class)
    @DisplayName("Posting with unsupported ThreadingMode reverts to default ThreadingMode.")
    public void postingWithUnsupportedThreadingModeRevertsToDefaultThreadingMode(
            final ThreadingMode unsupportedThreadingMode
    ) {
        final Set<ThreadingMode> supportedThreadingModeSet = EnumSet.allOf(ThreadingMode.class);
        supportedThreadingModeSet.remove(unsupportedThreadingMode);

        final int eventCount = 97;
        for (final ThreadingMode defaultThreadingMode : supportedThreadingModeSet) {
            final AtomicInteger receivedEventCount = new AtomicInteger(0);
            try (final TestingAbstractLambdaBus lb = new TestingAbstractLambdaBus(defaultThreadingMode, supportedThreadingModeSet)) {
                final Consumer<TestEvent> testSubscriber = (event) -> {
                    receivedEventCount.incrementAndGet();
                };

                lb.subscribe(TestEvent.class, testSubscriber);
                for (int i = 0; i < eventCount; i++) {
                    /*
                     * If the received ThreadingMode is supported will be tested
                     * by the testing bus implementation.
                     */
                    lb.post(new TestEvent(), unsupportedThreadingMode);
                }
                assertEquals(eventCount, receivedEventCount.get());
            }
        }

    }

    //##########################################################################
    // Our own private event class for tests
    //##########################################################################

    private static class TestEvent { };

    //##########################################################################
    // Helper class to instantiate the abstract class to be tested
    //##########################################################################

    private final static class TestingAbstractLambdaBus
        extends AbstractLambdaBus {

        private static final ThreadingMode DEFAULT_THREADING_MODE = ThreadingMode.SYNC;

        private final Set<ThreadingMode> supportedThreadingModes;

        protected TestingAbstractLambdaBus(
        ) {
            this(
                    DEFAULT_THREADING_MODE,
                    EnumSet.of(DEFAULT_THREADING_MODE));
        }

        protected TestingAbstractLambdaBus(
                final ThreadingMode defaultThreadingMode,
                final Set<ThreadingMode> supportedThreadingModes
        ) {
            super(defaultThreadingMode, supportedThreadingModes);

            assertNotNull(defaultThreadingMode, "'defaultThreadingMode' must not be null");

            assertNotNull(supportedThreadingModes, "'supportedThreadingModes' must not be null");
            assertTrue(supportedThreadingModes.contains(defaultThreadingMode), "'supportedThreadingModes' must contain 'defaultThreadingMode'");

            assertFalse(supportedThreadingModes.contains(null), "'supportedThreadingModes' must not contain NULL");

            this.supportedThreadingModes = supportedThreadingModes;
        }

        @Override
        protected <T> void acceptNonNullEvent(
                final T event,
                final ThreadingMode supportedThreadingMode
        ) {
            assertNotNull(event, "'event' must not be null");

            assertNotNull(supportedThreadingMode, "'supportedThreadingMode' must not be null");
            // unsupported ThreadingModes should have been replaced when reaching this point
            assertTrue(isSupportedThreadingMode(supportedThreadingMode), "'supportedThreadingMode' must be supported");

            tryToDispatchNonNullEvent(event, supportedThreadingMode);
        }

        @Override
        protected <T> void dispatchNonNullEventToSubscriber(
                T event,
                Collection<Consumer<T>> eventSubscriberCollection,
                ThreadingMode supportedThreadingMode
        ) {
            assertNotNull(event, "'event' must not be null");

            assertNotNull(eventSubscriberCollection, "'eventSubscriberCollection' must not be null");
            assertFalse(eventSubscriberCollection.isEmpty(), "'eventSubscriberCollection' must not be empty");

            assertNotNull(supportedThreadingMode, "'supportedThreadingMode' must not be null");

            // unsupported ThreadingModes should have been replaced when reaching this point
            assertTrue(isSupportedThreadingMode(supportedThreadingMode), "'supportedThreadingMode' must be supported");
            assertTrue(supportedThreadingModes.contains(supportedThreadingMode), "'supportedThreadingMode' must be supported");

            DispatchingUtil.dispatchEventToSubscriber(
                event,
                eventSubscriberCollection);
        }

    }

    final static class TestingAbstractLambdaBusTest {

        @Test
        @DisplayName("Constructor - Setting null defaultThreadingMode throws Error")
        public void constructorWithNullDefaultThreadingModesThrowsError() {
            assertThrows(
                AssertionError.class,
                () -> new TestingAbstractLambdaBus(null, EnumSet.allOf(ThreadingMode.class)));
        }

        @ParameterizedTest(name = "Constructor - {0} - Setting null supportedThreadingModes throws Error")
        @EnumSource(value=ThreadingMode.class)
        @DisplayName("Constructor - Setting null supportedThreadingModes throws Error")
        public void constructorWithNullSupportedThreadingModesThrowsError(
                final ThreadingMode threadingMode
        ) {
            assertThrows(
                AssertionError.class,
                () -> new TestingAbstractLambdaBus(threadingMode, null));
        }

        @ParameterizedTest(name = "Constructor - {0} - Setting empty supportedThreadingModes throws Error")
        @EnumSource(value=ThreadingMode.class)
        @DisplayName("Constructor - Setting empty supportedThreadingModes throws Error")
        public void constructorWithEmptySupportedThreadingModesThrowsError(
                final ThreadingMode threadingMode
        ) {
            assertThrows(
                AssertionError.class,
                () -> new TestingAbstractLambdaBus(threadingMode, Collections.emptySet()));
        }

        @ParameterizedTest(name = "Constructor - {0} - Setting supportedThreadingModes containing null ThreadingMode throws Error")
        @EnumSource(value=ThreadingMode.class)
        @DisplayName("Constructor - Setting supportedThreadingModes containing null ThreadingMode throws Error")
        public void constructorWithSupportedThreadingModesContainingNullThreadingModeThrowsError(
                final ThreadingMode threadingMode
        ) {
            final Set<ThreadingMode> allThreadingModesAndNull = new HashSet<>(EnumSet.allOf(ThreadingMode.class));
            allThreadingModesAndNull.add(null);
            assertThrows(
                AssertionError.class,
                () -> new TestingAbstractLambdaBus(threadingMode, allThreadingModesAndNull));
        }

        @ParameterizedTest(name = "Constructor - Setting unsupported default ThreadingMode.{0} throws Error")
        @EnumSource(value=ThreadingMode.class)
        @DisplayName("Constructor - Setting unsupported default ThreadingMode throws Error")
        public void constructorWithUnsupportedDefaultThreadingModeThrowsError(
                final ThreadingMode threadingMode
        ) {
            final Set<ThreadingMode> allOtherThreadingModes = EnumSet.allOf(ThreadingMode.class);
            allOtherThreadingModes.remove(threadingMode);
            assertThrows(
                AssertionError.class,
                () -> new TestingAbstractLambdaBus(threadingMode, allOtherThreadingModes));
        }

    }

}
