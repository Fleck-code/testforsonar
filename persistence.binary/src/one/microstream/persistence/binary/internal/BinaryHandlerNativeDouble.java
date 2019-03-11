package one.microstream.persistence.binary.internal;

import one.microstream.persistence.binary.types.Binary;
import one.microstream.persistence.types.PersistenceStoreHandler;

public final class BinaryHandlerNativeDouble extends AbstractBinaryHandlerNativeCustomValueFixedLength<Double>
{
	///////////////////////////////////////////////////////////////////////////
	// constructors     //
	/////////////////////

	public BinaryHandlerNativeDouble()
	{
		super(Double.class, defineValueType(double.class));
	}



	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public void store(final Binary bytes, final Double instance, final long oid, final PersistenceStoreHandler handler)
	{
		bytes.storeDouble(this.typeId(), oid, instance.doubleValue());
	}

	@Override
	public Double create(final Binary bytes)
	{
		return bytes.buildDouble();
	}

}