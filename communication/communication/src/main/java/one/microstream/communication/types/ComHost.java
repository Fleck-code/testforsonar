package one.microstream.communication.types;

/*-
 * #%L
 * microstream-communication
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

import static one.microstream.X.mayNull;
import static one.microstream.X.notNull;

import java.net.InetSocketAddress;

/**
 * Host type to listen for new connections and relay them to logic for further processing,
 * potentially in another, maybe even dedicated thread.
 * 
 * 
 * @param <C> the communication layer type
 */
public interface ComHost<C> extends Runnable
{
	public InetSocketAddress address();
	
	public ComProtocolProvider<C> protocolProvider();
	
	/**
	 * Listens for incoming connections and relays them for processing.
	 */
	public void acceptConnections();
	
	@Override
	public void run();
	
	public void stop();
	
	public boolean isRunning();
	
	
	
	public static <C> ComHost<C> New(
		final InetSocketAddress        address           ,
		final ComConnectionHandler<C>  connectionHandler ,
		final ComConnectionAcceptor<C> connectionAcceptor
	)
	{
		return new ComHost.Default<>(
			mayNull(address)           ,
			notNull(connectionHandler) ,
			notNull(connectionAcceptor)
		);
	}
	
	public final class Default<C> implements ComHost<C>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final InetSocketAddress        address           ;
		private final ComConnectionHandler<C>  connectionHandler ;
		private final ComConnectionAcceptor<C> connectionAcceptor;
		
		private transient ComConnectionListener<C> liveConnectionListener;
		
		
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(
			final InetSocketAddress        address           ,
			final ComConnectionHandler<C>  connectionHandler ,
			final ComConnectionAcceptor<C> connectionAcceptor
		)
		{
			super();
			this.address            = address           ;
			this.connectionHandler  = connectionHandler ;
			this.connectionAcceptor = connectionAcceptor;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final InetSocketAddress address()
		{
			return this.address;
		}

		@Override
		public final ComProtocolProvider<C> protocolProvider()
		{
			return this.connectionAcceptor.protocolProvider();
		}

		@Override
		public void run()
		{
			// the whole method may not be synchronized, otherweise a running host could never be stopped
			synchronized(this)
			{
				if(this.isRunning())
				{
					// if the host is already running, this method must abort here.
					return;
				}
				
				this.liveConnectionListener = this.connectionHandler.createConnectionListener(this.address);
			}
			
			this.acceptConnections();
		}
		
		@Override
		public synchronized void stop()
		{
			if(this.liveConnectionListener == null)
			{
				return;
			}
			
			this.liveConnectionListener.close();
			this.liveConnectionListener = null;
		}

		@Override
		public synchronized boolean isRunning()
		{
			return this.liveConnectionListener != null;
		}

		@Override
		public void acceptConnections()
		{
			// repeatedly accept new connections until stopped.
			while(true)
			{
				synchronized(this)
				{
					if(!this.isRunning())
					{
						break;
					}
					
					this.synchAcceptConnection();
				}
			}
		}
		
		private void synchAcceptConnection()
		{
			final C connection = this.liveConnectionListener.listenForConnection();
			this.connectionAcceptor.acceptConnection(connection, this);
		}
	}
	
	
	
	public static <C> ComHostCreator<C> Creator()
	{
		return ComHostCreator.New();
	}
	
}
