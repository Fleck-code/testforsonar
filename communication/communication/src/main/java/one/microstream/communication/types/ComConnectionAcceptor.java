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

import static one.microstream.X.notNull;


/**
 * Logic to greet/authenticate the client, exchange metadata, create a {@link ComChannel} instance.
 * Potentially in another, maybe even dedicated thread.
 * 
 * 
 * @param <C> the communication layer type
 */
public interface ComConnectionAcceptor<C>
{
	public ComProtocolProvider<C> protocolProvider();
	
	public void acceptConnection(C connection, ComHost<C> parent);
	
	
	
	public static <C> ComConnectionAcceptorCreator<C> Creator()
	{
		return ComConnectionAcceptorCreator.New();
	}
	
	public static <C> ComConnectionAcceptor<C> New(
		final ComProtocolProvider<C>     protocolProvider       ,
		final ComProtocolStringConverter protocolStringConverter,
		final ComConnectionHandler<C>    connectionHandler      ,
		final ComPersistenceAdaptor<C>   persistenceAdaptor     ,
		final ComHostChannelAcceptor<C>  channelAcceptor
	)
	{
		
		return new ComConnectionAcceptor.Default<>(
			notNull(protocolProvider)       ,
			notNull(protocolStringConverter),
			notNull(connectionHandler)      ,
			notNull(persistenceAdaptor)     ,
			notNull(channelAcceptor)
		);
	}
	
	public final class Default<C> implements ComConnectionAcceptor<C>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final ComProtocolProvider<C>     protocolProvider       ;
		private final ComProtocolStringConverter protocolStringConverter;
		private final ComConnectionHandler<C>    connectionHandler      ;
		private final ComPersistenceAdaptor<C>   persistenceAdaptor     ;
		private final ComHostChannelAcceptor<C>  channelAcceptor        ;
				
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(
			final ComProtocolProvider<C>     protocolProvider       ,
			final ComProtocolStringConverter protocolStringConverter,
			final ComConnectionHandler<C>    connectionHandler      ,
			final ComPersistenceAdaptor<C>   persistenceAdaptor     ,
			final ComHostChannelAcceptor<C>  channelAcceptor
		)
		{
			super();
			this.protocolProvider        = protocolProvider       ;
			this.protocolStringConverter = protocolStringConverter;
			this.connectionHandler       = connectionHandler      ;
			this.persistenceAdaptor      = persistenceAdaptor     ;
			this.channelAcceptor         = channelAcceptor        ;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final ComProtocolProvider<C> protocolProvider()
		{
			return this.protocolProvider;
		}
		
		@Override
		public final void acceptConnection(final C connection, final ComHost<C> parent)
		{
			// note: things like authentication could be done here in a wrapping implementation.
			
			final ComProtocol protocol = this.protocolProvider.provideProtocol(connection);
			this.connectionHandler.sendProtocol(connection, protocol, this.protocolStringConverter);
			
			final ComHostChannel<C> channel = this.persistenceAdaptor.createHostChannel(connection, protocol, parent);
			this.channelAcceptor.acceptChannel(channel);
		}
		
	}
	
}
