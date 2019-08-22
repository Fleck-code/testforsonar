package one.microstream.persistence.binary.internal;

import one.microstream.X;
import one.microstream.collections.Singleton;
import one.microstream.collections.types.XGettingEnum;
import one.microstream.persistence.binary.types.Binary;
import one.microstream.persistence.types.Persistence;
import one.microstream.persistence.types.PersistenceObjectIdResolver;
import one.microstream.persistence.types.PersistenceStoreHandler;
import one.microstream.persistence.types.PersistenceTypeDefinitionMember;
import one.microstream.persistence.types.PersistenceTypeDefinitionMemberEnumConstant;
import one.microstream.reflect.XReflect;

public final class BinaryHandlerSingletonStatelessEnum<T> extends AbstractBinaryHandlerTrivial<T>
{
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	public static boolean isSingletonEnumType(final Class<?> type)
	{
		return XReflect.isEnum(type) && type.getEnumConstants().length == 1;
	}
	
	public static <T> Class<T> validateIsSingletonEnumType(final Class<T> type)
	{
		if(isSingletonEnumType(type))
		{
			return type;
		}
		
		throw new IllegalArgumentException("Not a singleton Enum type: " + type);
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends Enum<T>> BinaryHandlerSingletonStatelessEnum<T> New(final Class<?> type)
	{
		return new BinaryHandlerSingletonStatelessEnum<>(
			(Class<T>)XReflect.validateIsEnum(type)
		);
	}
	
	private final Singleton<PersistenceTypeDefinitionMemberEnumConstant> enumConstantMember;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	protected BinaryHandlerSingletonStatelessEnum(final Class<T> type)
	{
		super(validateIsSingletonEnumType(type));
		this.enumConstantMember = X.Singleton(
			BinaryHandlerGenericEnum.deriveEnumConstantMembers(type).get()
		);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public final Object[] collectEnumConstants()
	{
		// single enum constant has already been validated by constructor logic
		return Persistence.collectEnumConstants(this);
	}
	
	@Override
	public final XGettingEnum<? extends PersistenceTypeDefinitionMember> allMembers()
	{
		return this.enumConstantMember;
	}

	@Override
	public final void store(
		final Binary                  bytes   ,
		final T                       instance,
		final long                    objectId,
		final PersistenceStoreHandler handler
	)
	{
		bytes.storeEntityHeader(0, this.typeId(), objectId);
	}

	@SuppressWarnings("unchecked")
	@Override
	public final T create(final Binary medium, final PersistenceObjectIdResolver idResolver)
	{
		return (T)XReflect.getDeclaredEnumClass(this.type()).getEnumConstants()[0];
	}
	
}