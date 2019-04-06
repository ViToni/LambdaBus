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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kromo.lambdabus.dispatcher.EventDispatcher;
import org.mockito.Mockito;

import org.kromo.lambdabus.ThreadingMode;

/**
 * Test for the {@link DispatchingLambdaBus}
 *
 * @author Victor Toni - initial implementation
 *
 */
public class DispatchingLambdaBusTest {

    @Test
    @DisplayName("Constructor with null EventDispatcher throws NullPointerException")
    public void constructor_with_null_EventDispatcher_throws_NPE() {
        final EventDispatcher nullEventDispatcher = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final DispatchingLambdaBus lb = new DispatchingLambdaBus(
                            nullEventDispatcher)) {}
                }
        );
    }

    @Test
    @DisplayName("Default Constructor")
    public void defaultConstructor() {
        final EventDispatcher eventDispatcher = createMockEventDispatcher();

        assertDoesNotThrow(
                () -> {
                    try (final DispatchingLambdaBus lb = new DispatchingLambdaBus(
                            eventDispatcher)) {}
                }
        );
    }

    @Test
    @DisplayName("Set / unset default RunnableForNullEvent")
    public void setDefaultRunnableForNullEventAndUnsetDefaultRunnableForNullEvent() {
        try (final EventDispatcher eventDispatcher = createMockEventDispatcher()) {
            try (final DispatchingLambdaBus lb = new DispatchingLambdaBus(eventDispatcher)) {
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
    }

    @Test
    @DisplayName("Setting null RunnableForNullEvent throws NullPointerException")
    public void settingNullRunnableForNullEventThrows_NPE() {
        try (final EventDispatcher eventDispatcher = createMockEventDispatcher()) {
            try (final DispatchingLambdaBus lb = new DispatchingLambdaBus(eventDispatcher)) {

                assertThrows(
                    NullPointerException.class,
                    () -> lb.setRunnableForNullEvent(null));
            }
        }
    }

    @Test
    @DisplayName("Setting RunnableForNullEvent on closed bus throws IllegalStateException")
    public void settingRunnableForNullEventOnClosedLambdaBusThrowsIllegalStateException() {
        try (final EventDispatcher eventDispatcher = createMockEventDispatcher()) {
            try (final DispatchingLambdaBus lb = new DispatchingLambdaBus(eventDispatcher)) {

                lb.close();

                assertThrows(
                    IllegalStateException.class,
                    () -> lb.setRunnableForNullEvent(System.out::println));
            }
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
            final AtomicInteger receivedEventCount = new AtomicInteger();
            try (final EventDispatcher eventDispatcher = createMockEventDispatcher(defaultThreadingMode, supportedThreadingModeSet)) {
                try (final DispatchingLambdaBus lb = new DispatchingLambdaBus(eventDispatcher)) {
                    final Consumer<TestEvent> testSubscriber = event -> receivedEventCount.incrementAndGet();

                    lb.subscribe(TestEvent.class, testSubscriber);
                    for (int i = 0; i < eventCount; i++) {
                        final Object event = new TestEvent();
                        /*
                         * If the received ThreadingMode is supported will be tested
                         * by the testing bus implementation.
                         */
                        lb.post(event, unsupportedThreadingMode);


                        // 1) assert the event is the same as the one posted
                        // 2) the ThreadingMode is not same as the one posted but the default one
                        // since the posted one is not supported
                        Mockito //
                            .verify(eventDispatcher, times(1)) //
                                .dispatchEventToHandler( //
                                        Mockito.same(event), //
                                        Mockito.anyCollection(), //
                                        Mockito.same(defaultThreadingMode) //
                        );

                    }
                    Mockito //
                        .verify(eventDispatcher, times(eventCount)) //
                            .dispatchEventToHandler( //
                                    Mockito.any(TestEvent.class), //
                                    Mockito.anyCollection(), //
                                    Mockito.same(defaultThreadingMode) //
                            );
                }
            }
        }
    }

    //##########################################################################
    // Setting up mocked EventDispatcher
    //##########################################################################

    EventDispatcher createMockEventDispatcher() {
        return createMockEventDispatcher(ThreadingMode.SYNC, EnumSet.of(ThreadingMode.SYNC));
    }

    EventDispatcher createMockEventDispatcher(
            final ThreadingMode defaultThreadingMode,
            final Set<ThreadingMode> supportedThreadingModes
    ) {
        final EventDispatcher eventDispatcher = Mockito.mock(EventDispatcher.class);
        Mockito //
            .doReturn(defaultThreadingMode) //
            .when(eventDispatcher).getDefaultThreadingMode();
        Mockito //
            .doReturn(supportedThreadingModes) //
            .when(eventDispatcher).getSupportedThreadingModes();

        return eventDispatcher;
    }

    //##########################################################################
    // Our own private event class for tests
    //##########################################################################

    private static class TestEvent { }


}
