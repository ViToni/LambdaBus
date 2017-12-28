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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.impl.concurrent.NonTerminatingExecutorService;

/**
 * Testing {@link QueuedLambdaBus} reusing tests from
 * {@link AbstractThreadedLambdaBusContract}. Custom test are added for
 * {@link QueuedLambdaBus} constructors.
 * 
 * @author Victor Toni - initial implementation
 *
 */
public class QueuedLambdaBusTest
    extends AbstractThreadedLambdaBusContract<QueuedLambdaBus> {

    protected QueuedLambdaBus createLambdaBus() {
        return new QueuedLambdaBus();
    }

    @DisplayName("Constructor with non-terminating decorated ExecutorService")
    @Test
    public void constructorWithExternalExecutorService() {
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final ExecutorService nonTerminatingExecutorService = new NonTerminatingExecutorService(executorService);
        try {
            assertDoesNotThrow(
                    () -> {
                        try (final LambdaBus lb = new QueuedLambdaBus(nonTerminatingExecutorService)) {}
                    }
            );
            assertFalse(executorService.isShutdown(), "Non-terminating decorated ExecutorService must not be shutdown");
        } finally {
            executorService.shutdownNow();
        }
    }

    @DisplayName("Constructor with default ThreadingMode and non-terminating decorated ExecutorService")
    @ParameterizedTest(name = "Constructor with ThreadingMode.{0} as default and non-terminating decorated ExecutorService")
    @EnumSource(ThreadingMode.class)
    public void constructorWithDefaultThreadingModeAndExternalExecutorService(
            final ThreadingMode defaultThreadingMode
    ) {
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final ExecutorService nonTerminatingExecutorService = new NonTerminatingExecutorService(executorService);
        try {
            assertDoesNotThrow(
                    () -> {
                        try (final LambdaBus lb = new QueuedLambdaBus(defaultThreadingMode, nonTerminatingExecutorService)) {}
                    }
            );
            assertFalse(executorService.isShutdown(), "Non-terminating decorated ExecutorService must not be shutdown");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("Constructor with null ThreadingMode throws NullPointerException")
    public void constructorNullThreadingModeThrowsNPE() {
        final ThreadingMode defaultThreadingMode = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final LambdaBus lb = new QueuedLambdaBus(defaultThreadingMode)) {}
                }
        );

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            assertThrows(
                    NullPointerException.class,
                    () -> {
                        try (final LambdaBus lb = new QueuedLambdaBus(defaultThreadingMode, executorService)) {}
                    }
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    @DisplayName("Constructor with null ExecutorService throws NullPointerException")
    public void constructorNullExecutorServiceThrowsNPE() {
        final ExecutorService nullExecutorService = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final LambdaBus lb = new QueuedLambdaBus(nullExecutorService)) {}
                }
        );
    }


    @DisplayName("Constructor with default ThreadingMode and null ExecutorService throws NullPointerException")
    @ParameterizedTest(name = "Constructor with ThreadingMode.{0} as default and null ExecutorService throws NullPointerException")
    @EnumSource(ThreadingMode.class)
    public void constructorNullExecutorServiceThrowsNPE(
            final ThreadingMode defaultThreadingMode
    ) {
        final ExecutorService nullExecutorService = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final LambdaBus lb = new QueuedLambdaBus(defaultThreadingMode, nullExecutorService)) {}
                }
        );
    }

}
