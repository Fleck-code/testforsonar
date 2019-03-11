package one.microstream.persistence.binary.internal;

import one.microstream.persistence.binary.types.Binary;
import one.microstream.persistence.types.PersistenceStoreHandler;

public final class BinaryHandlerNativeCharacter extends AbstractBinaryHandlerNativeCustomValueFixedLength<Character>
{
	///////////////////////////////////////////////////////////////////////////
	// constructors     //
	/////////////////////

	public BinaryHandlerNativeCharacter()
	{
		super(Character.class, defineValueType(char.class));
	}



	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public void store(final Binary bytes, final Character instance, final long oid, final PersistenceStoreHandler handler)
	{
		bytes.storeCharacter(this.typeId(), oid, instance.charValue());
	}

	@Override
	public Character create(final Binary bytes)
	{
		return bytes.buildCharacter();
	}

}