/**
 *
 */
package one.microstream.exceptions;

/*-
 * #%L
 * microstream-base
 * %%
 * Copyright (C) 2019 - 2021 MicroStream Software
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */


/**
 * Checked exceptions are a badly designed concept that ruin functional programming and seduce to swallow exceptions
 * with a foolish print instead of handling them properly and ignore unchecked exceptions altogether.
 * 
 * 
 *
 */
public class WrapperRuntimeException extends RuntimeException
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final Exception actual;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public WrapperRuntimeException(final Exception actual)
	{
		super(actual);
		this.actual = actual;
		// can't use fillInStackTrace() because of diletantic constructor-intrinsic call in Throwable
//		this.setStackTrace(this.actual.getStackTrace());
	}



	///////////////////////////////////////////////////////////////////////////
	// getters //
	////////////

	public Exception getActual()
	{
		return this.actual;
	}



	///////////////////////////////////////////////////////////////////////////
	// override methods //
	/////////////////////

//	@Override
//	public synchronized Throwable getCause()
//	{
//		return this.actual.getCause();
//	}
//
//	@Override
//	public StackTraceElement[] getStackTrace()
//	{
//		return this.actual.getStackTrace();
//	}
//
//	@Override
//	public String getMessage()
//	{
//		return this.actual.getMessage();
//	}
//
//	@Override
//	public synchronized Throwable fillInStackTrace()
//	{
//		return this;
//	}

}
