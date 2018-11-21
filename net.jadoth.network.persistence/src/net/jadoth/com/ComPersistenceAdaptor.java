package net.jadoth.com;

import java.util.function.Consumer;

import net.jadoth.collections.types.XGettingEnum;
import net.jadoth.persistence.types.Persistence;
import net.jadoth.persistence.types.PersistenceFoundation;
import net.jadoth.persistence.types.PersistenceManager;
import net.jadoth.persistence.types.PersistenceTypeDictionary;
import net.jadoth.persistence.types.PersistenceTypeDictionaryCompiler;
import net.jadoth.persistence.types.PersistenceTypeDictionaryManager;
import net.jadoth.persistence.types.PersistenceTypeDictionaryView;
import net.jadoth.persistence.types.PersistenceTypeDictionaryViewProvider;
import net.jadoth.persistence.types.PersistenceTypeHandlerManager;
import net.jadoth.swizzling.types.SwizzleIdStrategy;


public interface ComPersistenceAdaptor<C> extends PersistenceTypeDictionaryViewProvider
{
	@Override
	public default PersistenceTypeDictionaryView provideTypeDictionary()
	{
		final PersistenceFoundation<?, ?> initFoundation = this.createInitializationFoundation();
		
		initFoundation.setTypeDictionaryManager(
			PersistenceTypeDictionaryManager.Transient(
				initFoundation.getTypeDictionaryCreator()
			)
		);
		
		final SwizzleIdStrategy idStrategy = this.hostInitializationIdStrategy();
		initFoundation.setObjectIdProvider(idStrategy.createObjectIdProvider());
		initFoundation.setTypeIdProvider(idStrategy.createTypeIdProvider());

		final PersistenceTypeHandlerManager<?> thm = initFoundation.getTypeHandlerManager();
		thm.initialize();
		
		this.iterateEntityTypes(c ->
			thm.ensureTypeHandler(c)
		);
		
		final PersistenceTypeDictionary typeDictionary = thm.typeDictionary();
		
		final PersistenceTypeDictionaryView typeDictionaryView = typeDictionary.view();
		
		return typeDictionaryView;
	}
	
	public PersistenceFoundation<?, ?> createInitializationFoundation();
	
	public void iterateEntityTypes(final Consumer<? super Class<?>> iterator);
	
	public SwizzleIdStrategy hostInitializationIdStrategy();
	
	public SwizzleIdStrategy hostIdStrategy();
	
	public default PersistenceTypeDictionaryCompiler provideTypeDictionaryCompiler()
	{
		return this.provideHostPersistenceFoundation(null)
			.getTypeDictionaryCompiler()
		;
	}
	
	/**
	 * Might return the same instance for all connections or the same for every unique client or a new instance on
	 * every call. Depends on the use-case.<br>
	 * The persistence medium type used by the persistence manager is irrelevant on the com-level, hence the "?".
	 * 
	 * @param connection
	 * @return
	 */
	public default PersistenceManager<?> provideHostPersistenceManager(
		final C connection
	)
	{
		return this.provideHostPersistenceFoundation(connection)
			.createPersistenceManager()
		;
	}
	
	public default PersistenceManager<?> provideClientPersistenceManager(
		final C           connection,
		final ComProtocol protocol
	)
	{
		return this.provideClientPersistenceFoundation(connection, protocol)
			.createPersistenceManager()
		;
	}
	
	/**
	 * Provides a {@link PersistenceFoundation} instance prepared for the passed connection instance.
	 * The passed connection instance might be null, in which case the returned foundation instance
	 * can only be used for general, non-communication-related operations.<p>
	 * See {@link #providePersistenceManager(C)} with a passed non-null connection instance.<br>
	 * See {@link #provideTypeDictionaryCompiler(C)} with a passed null connection instance.
	 * 
	 * @param connection
	 * @return
	 * 
	 * @see #providePersistenceManager(C)
	 * @see #provideTypeDictionaryCompiler()
	 */
	public PersistenceFoundation<?, ?> provideHostPersistenceFoundation(C connection);

	public PersistenceFoundation<?, ?> provideClientPersistenceFoundation(C connection, ComProtocol protocol);
	
	public default ComPersistenceAdaptor<C> initializePersistenceFoundation(
		final PersistenceTypeDictionaryViewProvider typeDictionaryProvider,
		final SwizzleIdStrategy                     idStrategy
	)
	{
		final PersistenceTypeDictionaryManager typeDictionaryManager =
			PersistenceTypeDictionaryManager.Immutable(typeDictionaryProvider)
		;
		
		final PersistenceFoundation<?, ?> foundation = this.persistenceFoundation();
		foundation.setTypeDictionaryManager(typeDictionaryManager);
		foundation.setObjectIdProvider     (idStrategy.createObjectIdProvider());
		foundation.setTypeIdProvider       (idStrategy.createTypeIdProvider());
		
		/*
		 * Communication differs from Storing in some essential details, so the OGS Legacy Type Mapping
		 * is not applicable here.
		 * Also see descriptions in Issue JET-46. At some point in the future, a OGC-suitable type mapping
		 * will probably become necessary. Until then, type mismatches are invalid.
		 */
		foundation.setTypeMismatchValidator(Persistence.typeMismatchValidatorFailing());
		
		return this;
	}
	
	public default ComHostChannel<C> createHostChannel(
		final C           connection,
		final ComProtocol protocol  ,
		final ComHost<C>  parent
	)
	{
		final PersistenceManager<?> pm = this.provideHostPersistenceManager(connection);
		
		return ComHostChannel.New(pm, connection, protocol, parent);
	}
	
	public default ComClientChannel<C> createClientChannel(
		final C            connection,
		final ComProtocol  protocol  ,
		final ComClient<C> parent
	)
	{
		final PersistenceManager<?> pm = this.provideClientPersistenceManager(connection, protocol);
		
		return ComClientChannel.New(pm, connection, protocol, parent);
	}
	
	public PersistenceFoundation<?, ?> persistenceFoundation();
	
	public default ComPersistenceAdaptor<C> initializeClientPersistenceFoundation(
		final ComProtocol protocol
	)
	{
		this.initializePersistenceFoundation(protocol, protocol.idStrategy());
		
		return this;
	}
	
	public default ComPersistenceAdaptor<C> initializeHostPersistenceFoundation()
	{
		final PersistenceTypeDictionaryView typeDictionary = this.provideTypeDictionary();
		
		this.initializePersistenceFoundation(
			PersistenceTypeDictionaryViewProvider.Wrapper(typeDictionary),
			this.hostIdStrategy()
		);
		
		return this;
	}
	
	
	public abstract class Abstract<C> implements ComPersistenceAdaptor<C>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final SwizzleIdStrategy      hostInitIdStrategy;
		private final XGettingEnum<Class<?>> entityTypes       ;
		private final SwizzleIdStrategy      hostIdStrategy    ;
		
		private transient PersistenceTypeDictionaryView cachedTypeDictionary     ;
		private transient boolean                       initializedHostFoundation;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		protected Abstract(
			final SwizzleIdStrategy      hostInitIdStrategy,
			final XGettingEnum<Class<?>> entityTypes       ,
			final SwizzleIdStrategy      hostIdStrategy
		)
		{
			super();
			this.hostInitIdStrategy = hostInitIdStrategy;
			this.entityTypes        = entityTypes       ;
			this.hostIdStrategy     = hostIdStrategy    ;
		}

		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public SwizzleIdStrategy hostIdStrategy()
		{
			return this.hostIdStrategy;
		}
		
		@Override
		public SwizzleIdStrategy hostInitializationIdStrategy()
		{
			return this.hostInitIdStrategy;
		}
		
		@Override
		public void iterateEntityTypes(final Consumer<? super Class<?>> iterator)
		{
			this.entityTypes.iterate(iterator);
		}
		
		@Override
		public PersistenceTypeDictionaryView provideTypeDictionary()
		{
			if(this.cachedTypeDictionary == null)
			{
				synchronized(this)
				{
					// recheck after synch
					if(this.cachedTypeDictionary == null)
					{
						this.cachedTypeDictionary = ComPersistenceAdaptor.super.provideTypeDictionary();
					}
				}
			}
			
			return this.cachedTypeDictionary;
		}
		
		@Override
		public ComPersistenceAdaptor.Abstract<C> initializeHostPersistenceFoundation()
		{
			if(!this.initializedHostFoundation)
			{
				synchronized(this)
				{
					// recheck after synch
					if(!this.initializedHostFoundation)
					{
						ComPersistenceAdaptor.super.initializeHostPersistenceFoundation();
						this.initializedHostFoundation = true;
					}
				}
			}
			
			return this;
		}
	}
		
}