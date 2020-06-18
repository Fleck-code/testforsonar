package one.microstream.storage.types;

import static one.microstream.X.notNull;

import one.microstream.X;
import one.microstream.chars.XChars;
import one.microstream.collections.BulkList;
import one.microstream.collections.EqHashTable;
import one.microstream.storage.exceptions.StorageException;
import one.microstream.storage.exceptions.StorageExceptionBackupCopying;
import one.microstream.storage.exceptions.StorageExceptionBackupEmptyStorageBackupAhead;
import one.microstream.storage.exceptions.StorageExceptionBackupEmptyStorageForNonEmptyBackup;
import one.microstream.storage.exceptions.StorageExceptionBackupInconsistentFileLength;
import one.microstream.storage.types.StorageBackupHandler.Default.ChannelInventory;

public interface StorageBackupHandler extends Runnable, StorageActivePart
{
	public StorageBackupSetup setup();
	
	public void initialize(int channelIndex);
	
	public void synchronize(StorageInventory storageInventory);
	
	public void copyFilePart(
		StorageLiveFile<?> sourceFile    ,
		long               sourcePosition,
		long               length
	);
	
	public void truncateFile(
		StorageLiveFile<?> file     ,
		long               newLength
	);
	
	public void deleteFile(
		StorageLiveFile<?> file
	);
	
	public default StorageBackupHandler start()
	{
		this.setRunning(true);
		return this;
	}
	
	public default StorageBackupHandler stop()
	{
		this.setRunning(false);
		return this;
	}
	
	public boolean isRunning();
	
	@Override
	public boolean isActive();
	
	public StorageBackupHandler setRunning(boolean running);
	
	
	
	public static StorageBackupHandler New(
		final StorageBackupSetup         backupSetup        ,
		final int                        channelCount       ,
		final StorageBackupItemQueue     itemQueue          ,
		final StorageOperationController operationController,
		final StorageDataFileValidator   validator
	)
	{
		final StorageBackupFileProvider backupFileProvider = backupSetup.backupFileProvider();
		
		final ChannelInventory[] cis = X.Array(ChannelInventory.class, channelCount, i ->
		{
			return new ChannelInventory(i, backupFileProvider);
		});
		
		return new StorageBackupHandler.Default(
	                cis                 ,
			notNull(backupSetup)        ,
			notNull(itemQueue)          ,
			notNull(operationController),
			notNull(validator)
		);
	}
	
	public final class Default implements StorageBackupHandler
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final StorageBackupSetup         backupSetup        ;
		private final ChannelInventory[]         channelInventories ;
		private final StorageBackupItemQueue     itemQueue          ;
		private final StorageOperationController operationController;
		private final StorageDataFileValidator   validator          ;
		
		private boolean running; // being "ordered" to run.
		private boolean active ; // being actually active, e.g. executing the last loop before running check.
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(
			final ChannelInventory[]         channelInventories ,
			final StorageBackupSetup         backupSetup        ,
			final StorageBackupItemQueue     itemQueue          ,
			final StorageOperationController operationController,
			final StorageDataFileValidator   validator
		)
		{
			super();
			this.channelInventories  = channelInventories ;
			this.backupSetup         = backupSetup        ;
			this.itemQueue           = itemQueue          ;
			this.operationController = operationController;
			this.validator           = validator          ;
		}

		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final StorageBackupSetup setup()
		{
			return this.backupSetup;
		}
		
		@Override
		public final synchronized boolean isRunning()
		{
			return this.running;
		}
		
		@Override
		public final synchronized boolean isActive()
		{
			return this.active;
		}
		
		@Override
		public final synchronized StorageBackupHandler setRunning(final boolean running)
		{
			this.running = running;
			return this;
		}
		
		
		protected StorageBackupFile resolveBackupTargetFile(final StorageLiveFile<?> sourceFile)
		{
			if(sourceFile instanceof StorageLiveDataFile)
			{
				return this.resolveBackupTargetFile((StorageLiveDataFile)sourceFile);
			}
			if(sourceFile instanceof StorageLiveTransactionsFile)
			{
				return this.resolveBackupTargetFile((StorageLiveTransactionsFile)sourceFile);
			}
			
			// (15.06.2020 TM)EXCP: proper exception
			throw new RuntimeException("Unhandled File Type: " + XChars.systemString(sourceFile));
		}
		
		private StorageBackupDataFile resolveBackupTargetFile(final StorageLiveDataFile sourceFile)
		{
			return this.channelInventories[sourceFile.channelIndex()].ensureBackupFile(sourceFile);
		}
		
		private StorageBackupTransactionsFile resolveBackupTargetFile(final StorageLiveTransactionsFile sourceFile)
		{
			return this.channelInventories[sourceFile.channelIndex()].ensureBackupFile(sourceFile);
		}
		

		@Override
		public void initialize(final int channelIndex)
		{
			try
			{
				this.tryInitialize(channelIndex);
			}
			catch(final RuntimeException e)
			{
				this.operationController.registerDisruption(e);
				throw e;
			}
		}
		
		@Override
		public void synchronize(final StorageInventory storageInventory)
		{
			try
			{
				this.trySynchronize(storageInventory);
			}
			catch(final RuntimeException e)
			{
				this.operationController.registerDisruption(e);
				throw e;
			}
		}
		
		@Override
		public void run()
		{
			// must be the method instead of the field to check the lock but don't conver the whole loop
			try
			{
				this.active = true;
				
				// can not / may not copy storage files if the storage is not running (has locked and opend files, etc.)
				while(this.isRunning() && this.operationController.checkProcessingEnabled())
				{
					try
					{
						if(!this.itemQueue.processNextItem(this, 10_000))
						{
							this.validator.freeMemory();
						}
					}
					catch(final InterruptedException e)
					{
						// still not sure about the viability of interruption handling in a case like this.
						this.stop();
					}
					catch(final RuntimeException e)
					{
						this.operationController.registerDisruption(e);
						// see outer try-finally for cleanup
						throw e;
					}
				}
			}
			finally
			{
				// must close all open files on any aborting case (after stopping and before throwing an exception)
				this.closeAllDataFiles();
				this.active = false;
			}
			
		}
		
		private void tryInitialize(final int channelIndex)
		{
			final ChannelInventory backupInventory = this.channelInventories[channelIndex];
			backupInventory.ensureRegisteredFiles();
		}
		
		private void trySynchronize(final StorageInventory storageInventory)
		{
			final ChannelInventory backupInventory = this.channelInventories[storageInventory.channelIndex()];
			if(backupInventory.dataFiles.isEmpty())
			{
				this.fillEmptyBackup(storageInventory, backupInventory);
			}
			else
			{
				this.updateExistingBackup(storageInventory, backupInventory);
			}
		}
		
		final void fillEmptyBackup(
			final StorageInventory storageInventory,
			final ChannelInventory backupInventory
		)
		{
			for(final StorageLiveDataFile storageFile : storageInventory.dataFiles().values())
			{
				final StorageBackupDataFile backupTargetFile = this.resolveBackupTargetFile(storageFile);
				this.copyFile(storageFile, backupTargetFile);
			}
			
			final StorageLiveTransactionsFile transactionFile = storageInventory.transactionsFileAnalysis().transactionsFile();
			final StorageBackupTransactionsFile backupTransactionFile = this.resolveBackupTargetFile(transactionFile);
			this.copyFile(transactionFile, backupTransactionFile);
		}
		
		private void validateStorageInventoryForExistingBackup(
			final StorageInventory storageInventory,
			final ChannelInventory backupInventory
		)
		{
			if(!storageInventory.dataFiles().isEmpty())
			{
				return;
			}
			
			throw new StorageExceptionBackupEmptyStorageForNonEmptyBackup(
				backupInventory.channelIndex(),
				backupInventory.dataFiles()
			);
		}
		
		private void validateBackupFileProgress(
			final StorageInventory storageInventory,
			final ChannelInventory backupInventory
		)
		{
			final long lastStorageFileNumber = storageInventory.dataFiles().keys().last();
			final long lastBackupFileNumber  = backupInventory.dataFiles().keys().last();
			
			if(lastBackupFileNumber <= lastStorageFileNumber)
			{
				return;
			}
			
			throw new StorageExceptionBackupEmptyStorageBackupAhead(
				storageInventory,
				backupInventory.dataFiles()
			);
		}
		
		final void updateExistingBackup(
			final StorageInventory storageInventory,
			final ChannelInventory backupInventory
		)
		{
			this.validateStorageInventoryForExistingBackup(storageInventory, backupInventory);
			this.validateBackupFileProgress(storageInventory, backupInventory);

			final long lastBackupFileNumber = backupInventory.dataFiles().keys().last();
			for(final StorageLiveDataFile dataFile : storageInventory.dataFiles().values())
			{
				final StorageBackupDataFile backupTargetFile = this.resolveBackupTargetFile(dataFile);
				
				// non-existant files have either not been backupped, yet, or a "healable" error.
				if(!backupTargetFile.exists())
				{
					// in any case, the storage file is simply copied (backed up)
					this.copyFile(dataFile, backupTargetFile);
					continue;
				}
				
				final long storageFileLength      = dataFile.size();
				final long backupTargetFileLength = backupTargetFile.size();
				
				// existing file with matching length means everything is fine
				if(storageFileLength == backupTargetFileLength)
				{
					// continue with next file
					continue;
				}

				// the last/latest/highest existing backup file can validly diverge in length.
				if(backupTargetFile.number() == lastBackupFileNumber)
				{
					// missing length is copied to update the backup file
					this.copyFilePart(
						dataFile,
						backupTargetFileLength,
						storageFileLength - backupTargetFileLength,
						backupTargetFile
					);
					continue;
				}
				
				// any existing non-last backup file with divergent length is a consistency error
				throw new StorageExceptionBackupInconsistentFileLength(
					storageInventory           ,
					backupInventory.dataFiles(),
					dataFile                ,
					storageFileLength          ,
					backupTargetFile           ,
					backupTargetFileLength
				);
			}
			
			this.synchronizeTransactionFile(storageInventory, backupInventory);
		}
		
		private void deleteBackupTransactionFile(final ChannelInventory backupInventory)
		{
			final StorageBackupTransactionsFile backupTransactionFile = backupInventory.ensureTransactionsFile();
			if(!backupTransactionFile.exists())
			{
				return;
			}
			
			final StorageBackupTransactionsFile deletionTargetFile = this.backupSetup.backupFileProvider()
				.provideDeletionTargetFile(backupTransactionFile)
			;
			
			if(deletionTargetFile == null)
			{
				backupTransactionFile.delete();
			}
			else
			{
				backupTransactionFile.moveTo(deletionTargetFile);
			}
		}
		
		
		
		private void synchronizeTransactionFile(
			final StorageInventory storageInventory,
			final ChannelInventory backupInventory
		)
		{
			// tfa null means there is no transactions file. A non-existing transactions file later on is an error.
			final StorageTransactionsAnalysis tfa = storageInventory.transactionsFileAnalysis();
			if(tfa == null)
			{
				this.deleteBackupTransactionFile(backupInventory);
				return;
			}
			
			final StorageLiveTransactionsFile   liveTransactionsFile  = tfa.transactionsFile();
			final StorageBackupTransactionsFile backupTransactionFile = backupInventory.ensureTransactionsFile();
			
			if(!backupTransactionFile.exists())
			{
				// if the backup transaction file does not exist, yet, the actual file is simply copied.
				this.copyFile(liveTransactionsFile, backupTransactionFile);
				return;
			}

			final long storageFileLength      = liveTransactionsFile.size();
			final long backupTargetFileLength = backupTransactionFile.size();
			
			if(backupTargetFileLength != storageFileLength)
			{
				// on any mismatch, the backup transaction file is deleted (potentially moved&renamed) and rebuilt.
				this.deleteBackupTransactionFile(backupInventory);
				this.copyFile(liveTransactionsFile, backupTransactionFile);
			}
		}
				
		private void copyFile(
			final StorageLiveFile<?> storageFile     ,
			final StorageBackupFile  backupTargetFile
		)
		{
			storageFile.copyTo(backupTargetFile);
		}
		
		private void copyFilePart(
			final StorageLiveFile<?> sourceFile      ,
			final long               sourcePosition  ,
			final long               length          ,
			final StorageBackupFile  backupTargetFile
		)
		{
			try
			{
				final long oldBackupFileLength = backupTargetFile.size();
				
				try
				{
					sourceFile.copyTo(backupTargetFile, sourcePosition, length);
					
					// (16.06.2020 TM)TODO: nasty instanceof
					if(backupTargetFile instanceof StorageBackupDataFile)
					{
						this.validator.validateFile((StorageBackupDataFile)backupTargetFile, oldBackupFileLength, length);
					}
				}
				catch(final Exception e)
				{
					throw new StorageExceptionBackupCopying(sourceFile, sourcePosition, length, backupTargetFile, e);
				}
				finally
				{
					backupTargetFile.close();
				}
			}
			catch(final Exception e)
			{
				throw new StorageExceptionBackupCopying(sourceFile, sourcePosition, length, backupTargetFile, e);
			}
		}
		
		@Override
		public void copyFilePart(
			final StorageLiveFile<?> sourceFile    ,
			final long               sourcePosition,
			final long               copyLength
		)
		{
			// note: the original target file of the copying is irrelevant. Only the backup target file counts.
			final StorageBackupFile backupTargetFile = this.resolveBackupTargetFile(sourceFile);
			
			this.copyFilePart(sourceFile, sourcePosition, copyLength, backupTargetFile);
		}

		@Override
		public void truncateFile(
			final StorageLiveFile<?> file     ,
			final long               newLength
		)
		{
			final StorageBackupFile backupTargetFile = this.resolveBackupTargetFile(file);
			
			StorageFileWriter.truncateFile(backupTargetFile, newLength, this.backupSetup.backupFileProvider());
			
			// no user decrement since only the identifier is required and the actual file can well have been deleted.
		}
		
		@Override
		public void deleteFile(final StorageLiveFile<?> file)
		{
			final StorageBackupFile backupTargetFile = this.resolveBackupTargetFile(file);
			
			StorageFileWriter.deleteFile(backupTargetFile, this.backupSetup.backupFileProvider());
			
			// no user decrement since only the identifier is required and the actual file can well have been deleted.
		}
		
		final void closeAllDataFiles()
		{
			final DisruptionCollectorExecuting<StorageClosableFile> closer = DisruptionCollectorExecuting.New(file ->
				StorageClosableFile.close(file, null)
			);
			
			for(final ChannelInventory channel : this.channelInventories)
			{
				closer.executeOn(channel.transactionFile);
				for(final StorageBackupDataFile dataFile : channel.dataFiles.values())
				{
					closer.executeOn(dataFile);
				}
			}
			
			if(closer.hasDisruptions())
			{
				// (09.12.2019 TM)EXCP: proper exception
				throw new StorageException(closer.toMultiCauseException());
			}
		}
		
		
		
		static final class ChannelInventory implements StorageHashChannelPart
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			final int                                      channelIndex      ;
			final StorageBackupFileProvider                backupFileProvider;
			      StorageBackupTransactionsFile            transactionFile   ;
			      EqHashTable<Long, StorageBackupDataFile> dataFiles         ;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			ChannelInventory(
				final int                       channelIndex      ,
				final StorageBackupFileProvider backupFileProvider
			)
			{
				super();
				this.channelIndex       = channelIndex      ;
				this.backupFileProvider = backupFileProvider;
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////
			
			@Override
			public final int channelIndex()
			{
				return this.channelIndex;
			}
			
			public final EqHashTable<Long, StorageBackupDataFile> dataFiles()
			{
				return this.dataFiles;
			}
			
			final void ensureRegisteredFiles()
			{
				this.ensureDataFiles();
				this.ensureTransactionsFile();
			}
			
			final StorageBackupTransactionsFile ensureBackupFile(final StorageLiveTransactionsFile sourceFile)
			{
				if(this.transactionFile == null)
				{
					this.transactionFile = this.backupFileProvider.provideBackupFile(sourceFile);
				}
				
				return this.transactionFile;
			}
			
			
			final StorageBackupDataFile ensureBackupFile(final StorageLiveDataFile sourceFile)
			{
				// note: validation is done by the calling context, depending on its task.
				
				StorageBackupDataFile backupFile = this.dataFiles.get(sourceFile.number());
				if(backupFile == null)
				{
					backupFile = this.backupFileProvider.provideBackupFile(sourceFile);
					this.registerBackupFile(backupFile);
				}
				
				return backupFile;
			}
			
			private StorageBackupDataFile registerBackupFile(final StorageBackupDataFile backupFile)
			{
				this.dataFiles.add(backupFile.number(), backupFile);
				
				return backupFile;
			}
			
			final void ensureDataFiles()
			{
				if(this.dataFiles != null)
				{
					// files already registered
					return;
				}
				
				final BulkList<StorageBackupDataFile> collectedFiles =
				this.backupFileProvider.collectDataFiles(
					StorageBackupDataFile::New,
					BulkList.New(),
					this.channelIndex()
				)
				.sort(StorageDataFile::orderByNumber);
				
				this.dataFiles = EqHashTable.New();
				
				collectedFiles.iterate(this::registerBackupFile);
			}
			
			
		}
		
	}
	
}
