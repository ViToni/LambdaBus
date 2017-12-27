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

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.impl.AbstractLambdaBusContract;

/**
 * Extends the behavioral contract of the {@link LambdaBus}.<br>
 * 
 * @param <LambdaBusType>
 *            type to test which extends {@link AbstractThreadedLambdaBus}
 * 
 * @author Victor Toni - initial implementation
 *
 */
public abstract class AbstractThreadedLambdaBusContract<LambdaBusType extends AbstractThreadedLambdaBus>
    extends AbstractLambdaBusContract<LambdaBusType> {

}
