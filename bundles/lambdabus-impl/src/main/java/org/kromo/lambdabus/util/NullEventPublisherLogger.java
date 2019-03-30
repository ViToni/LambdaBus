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
 *     Victor Toni - initial API and implementation
 *******************************************************************************/
package org.kromo.lambdabus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class used to detect which publisher posted a {@code null} event.
 *
 * @author Victor Toni - initial API and implementation
 *
 */
public class NullEventPublisherLogger {

    private static final int ZERO = 0;

    private static final int DEFAULT_RELEVANT_STACKTRACE_ELEMENT_INDEX = 3;

    private final Logger log = LoggerFactory.getLogger(NullEventPublisherLogger.class);

    private final int relevantStackTraceElementIndex;

    public NullEventPublisherLogger() {
        this(DEFAULT_RELEVANT_STACKTRACE_ELEMENT_INDEX);
    }

    public NullEventPublisherLogger(final int relevantStackTraceElementIndex) {
        if (relevantStackTraceElementIndex < ZERO) {
            throw new IllegalArgumentException("'relevantStackTraceElementIndex' must not be less than ZERO");
        }

        this.relevantStackTraceElementIndex = relevantStackTraceElementIndex;
    }

    /**
     * Track down the source of the {@code null} event and log it.
     *
     * <p>Implementation note:<br>
     * Tracking down is done by creating an {@link Exception} and following the stack-trace.
     * Since this might be "expensive" it's only activated when TRACE level is enabled for logging.
     * </p>
     */
    public void logNullEventSource() {
        // if TRACE is NOT enabled don't create exceptions for nothing
        if (log.isTraceEnabled()) {
            final StackTraceElement[] stackTrace = new Exception().getStackTrace();
            if (relevantStackTraceElementIndex < stackTrace.length) {
                final StackTraceElement nullEventPublisher = stackTrace[relevantStackTraceElementIndex];
                log.trace("Null event posted by: {}", nullEventPublisher);
            } else {
                log.info("Failed to detect publisher of null event, not enough StackTraceElements({}) to be removed: {}",
                        stackTrace.length, relevantStackTraceElementIndex);
            }
        }
    }

}
