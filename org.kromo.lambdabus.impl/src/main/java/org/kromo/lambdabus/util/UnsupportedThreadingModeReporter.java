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
 *     Victor Toni - initial API and implementation
 */
package org.kromo.lambdabus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kromo.lambdabus.ThreadingMode;

/**
 * Helper class to centralize logging about usage of unsupported {@link ThreadingMode}s.
 *
 * @author Victor Toni - initial API and implementation
 *
 */
public class UnsupportedThreadingModeReporter {

    private static final UnsupportedThreadingModeReporter INSTANCE = new UnsupportedThreadingModeReporter();

    private final Logger logger = LoggerFactory.getLogger(UnsupportedThreadingModeReporter.class);

    private void internalReport(
            final ThreadingMode supportedThreadingMode,
            final ThreadingMode unsupportedThreadingMode
    ) {
        if (!supportedThreadingMode.equals(unsupportedThreadingMode)) {
            logger.warn("Unsupported ThreadingMode: {}, will use {}", unsupportedThreadingMode, supportedThreadingMode);
        }
    }

    public static void report(
            final ThreadingMode supportedThreadingMode,
            final ThreadingMode unsupportedThreadingMode
    ) {
        INSTANCE.internalReport(supportedThreadingMode, unsupportedThreadingMode);
    }
}
