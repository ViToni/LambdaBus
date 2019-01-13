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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.queue.EventQueue;
import org.kromo.lambdabus.queue.NonClosingEventQueue;
import org.kromo.lambdabus.queue.impl.SharableEventQueue;

/**
 * Testing {@link QueuedLambdaBus} reusing tests from {@link AbstractLambdaBusContract}.
 * <p>
 * Custom test are added for {@link QueuedLambdaBus} constructors.
 * 
 * @author Victor Toni - initial implementation
 *
 */
public class QueuedLambdaBusTest
    extends AbstractLambdaBusContract<QueuedLambdaBus> {

    protected QueuedLambdaBus createLambdaBus() {
        return new QueuedLambdaBus();
    }

    @DisplayName("Constructor with EventQueue")
    @Test
    public void constructorWithEventQueue() {
        try (final EventQueue eventQueue = new SharableEventQueue()) {
            assertDoesNotThrow(
                    () -> {
                        try (final LambdaBus lb = new QueuedLambdaBus(eventQueue)) {}
                    }
            );
        }
    }

    @DisplayName("Constructor with default ThreadingMode and EventQueue")
    @ParameterizedTest(name = "Constructor with ThreadingMode.{0} as default and EventQueue")
    @EnumSource(ThreadingMode.class)
    public void constructorWithDefaultThreadingModeAndEventQueue(
            final ThreadingMode defaultThreadingMode
    ) {
        // since this tests will use all possible ThreadingModes
        // we need to provide an ExecutorService for ASYNC_PER_EVENT and ASYNC_PER_SUBSCRIBER
        final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        try (final EventQueue eventQueue = new SharableEventQueue(executorService)) {
            assertDoesNotThrow(
                    () -> {
                        try (final LambdaBus lb = new QueuedLambdaBus(defaultThreadingMode, eventQueue)) {}
                    }
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("Constructor with null ThreadingMode throws NullPointerException")
    public void constructorNullThreadingModeThrowsNPE() {
        final ThreadingMode nullDefaultThreadingMode = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final LambdaBus lb = new QueuedLambdaBus(nullDefaultThreadingMode)) {}
                }
        );

        
        try (final EventQueue eventQueue = new SharableEventQueue()) {
            assertThrows(
                    NullPointerException.class,
                    () -> {
                        try (final LambdaBus lb = new QueuedLambdaBus(nullDefaultThreadingMode, eventQueue)) {}
                    }
            );
        }
    }

    @DisplayName("Constructor with null EventQueue throws NullPointerException")
    public void constructorNullEventQueueThrowsNPE() {
        final EventQueue nullEventQueue = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final LambdaBus lb = new QueuedLambdaBus(nullEventQueue)) {}
                }
        );
    }


    @DisplayName("Constructor with default ThreadingMode and null EventQueue throws NullPointerException")
    @ParameterizedTest(name = "Constructor with ThreadingMode.{0} as default and null EventQueue throws NullPointerException")
    @EnumSource(ThreadingMode.class)
    public void constructorNullEventQueueThrowsNPE(
            final ThreadingMode defaultThreadingMode
    ) {
        final EventQueue nullEventQueue = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final LambdaBus lb = new QueuedLambdaBus(defaultThreadingMode, nullEventQueue)) {}
                }
        );
    }

    @DisplayName("Closing QueuedLambdaBus closes EventQueue")
    @Test
    public void eventQueueGetsClosed() {
        try (final EventQueue eventQueue = new SharableEventQueue()) {
            final EventQueue nonClosingEventQueue = new NonClosingEventQueue(eventQueue);
            assertDoesNotThrow(
                    () -> {
                        try (final LambdaBus lb = new QueuedLambdaBus(nonClosingEventQueue)) {
                            assertFalse(nonClosingEventQueue.isClosed(), "Non-closing decorated EventQueue must not be closed");
                            assertFalse(eventQueue.isClosed(), "EventQueue must not be closed");
                        }
                    }
            );
            assertTrue(nonClosingEventQueue.isClosed(), "Non-closing decorated EventQueue must be closed");
            assertFalse(eventQueue.isClosed(), "EventQueue must not be closed");
        }
    }

}
