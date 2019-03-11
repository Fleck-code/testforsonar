package one.microstream.persistence.types;

public interface PersistenceTypeHandlerLookup<M> extends PersistenceTypeLookup
{
	public <T> PersistenceTypeHandler<M, T> lookupTypeHandler(T instance);

	public <T> PersistenceTypeHandler<M, T> lookupTypeHandler(Class<T> type);

	public PersistenceTypeHandler<M, ?> lookupTypeHandler(long typeId);

}