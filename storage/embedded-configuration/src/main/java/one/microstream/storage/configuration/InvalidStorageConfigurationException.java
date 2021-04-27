package one.microstream.storage.configuration;

/**
 * 
 * @deprecated will be removed in a future release
 * @see one.microstream.storage.configuration
 */
@Deprecated
public class InvalidStorageConfigurationException extends StorageConfigurationException
{
	public InvalidStorageConfigurationException()
	{
		super();
	}

	public InvalidStorageConfigurationException(
		final String    message           ,
		final Throwable cause             ,
		final boolean   enableSuppression ,
		final boolean   writableStackTrace
	)
	{
		super(
			message           ,
			cause             ,
			enableSuppression ,
			writableStackTrace
		);
	}

	public InvalidStorageConfigurationException(
		final String    message,
		final Throwable cause
	)
	{
		super(
			message,
			cause
		);
	}

	public InvalidStorageConfigurationException(
		final String message
	)
	{
		super(message);
	}

	public InvalidStorageConfigurationException(
		final Throwable cause
	)
	{
		super(cause);
	}
	
}