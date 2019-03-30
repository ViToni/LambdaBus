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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.impl.concurrent.NonTerminatingExecutorService;

/**
 * Tests for the {@link ThreadedLambdaBus}.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class ThreadedLambdaBusTest
    extends AbstractThreadedLambdaBusContract<ThreadedLambdaBus> {

    @Override
    protected ThreadedLambdaBus createLambdaBus() {
        return new ThreadedLambdaBus();
    }

    @Test
    @DisplayName("Constructor with non-terminating decorated ExecutorService")
    public void constructor_with_external_ExecutorService() {
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final ExecutorService nonTerminatingExecutorService = new NonTerminatingExecutorService(executorService);
        try {
            assertDoesNotThrow(
                    () -> {
                        try (final LambdaBus lb = new ThreadedLambdaBus(nonTerminatingExecutorService)) {}
                    }
            );
            assertFalse(executorService.isShutdown(), "Non-terminating decorated ExecutorService must not be shutdown");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("Constructor with non-terminating decorated ExecutorService")
    public void constructor_with_defaultThreadingMode_and_external_ExecutorService() {
        final ThreadingMode defaultThreadingMode = ThreadingMode.SYNC;
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final ExecutorService nonTerminatingExecutorService = new NonTerminatingExecutorService(executorService);
        try {
            assertDoesNotThrow(
                    () -> {
                        try (final LambdaBus lb = new ThreadedLambdaBus(defaultThreadingMode, nonTerminatingExecutorService)) {}
                    }
            );
            assertFalse(executorService.isShutdown(), "Non-terminating decorated ExecutorService must not be shutdown");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("Constructor with unsupported ThreadingMode throws IllegalArgumentException")
    public void constructor_unsupported_ThreadingMode_throws_IllegalArgumentException() {
        final ThreadingMode defaultThreadingMode = ThreadingMode.ASYNC;
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (final LambdaBus lb = new ThreadedLambdaBus(defaultThreadingMode)) {}
                }
        );

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (final LambdaBus lb = new ThreadedLambdaBus(defaultThreadingMode, executorService)) {}
                }
        );
    }

    @Test
    @DisplayName("Constructor with null ThreadingMode throws NullPointerException")
    public void constructor_null_ThreadingMode_throws_NPE() {
        final ThreadingMode defaultThreadingMode = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final LambdaBus lb = new ThreadedLambdaBus(defaultThreadingMode)) {}
                }
        );

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final LambdaBus lb = new ThreadedLambdaBus(defaultThreadingMode, executorService)) {}
                }
        );
    }

    @Test
    @DisplayName("Constructor with null ExecutorService throws NullPointerException")
    public void constructor_null_ExecutorService_throws_NPE() {
        final ExecutorService nullExecutorService = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final LambdaBus lb = new ThreadedLambdaBus(nullExecutorService)) {}
                }
        );

        final ThreadingMode defaultThreadingMode = ThreadingMode.SYNC;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final LambdaBus lb = new ThreadedLambdaBus(defaultThreadingMode, nullExecutorService)) {}
                }
        );
    }

}
