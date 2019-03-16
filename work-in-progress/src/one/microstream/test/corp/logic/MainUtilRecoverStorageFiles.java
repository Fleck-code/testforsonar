package one.microstream.test.corp.logic;

import static one.microstream.X.KeyValue;

import java.io.File;

import one.microstream.collections.BulkList;
import one.microstream.collections.EqHashTable;
import one.microstream.files.XFiles;
import one.microstream.meta.XDebug;
import one.microstream.persistence.binary.types.Binary;
import one.microstream.persistence.binary.types.BinaryEntityRawDataIterator;
import one.microstream.persistence.types.Persistence;
import one.microstream.storage.types.StorageDataFileValidator;
import one.microstream.storage.types.StorageEntityDataValidator;
import one.microstream.storage.types.StorageFileEntityDataIterator;
import one.microstream.storage.types.StorageFileProvider;
import one.microstream.storage.types.StorageNumberedFile;
import one.microstream.storage.types.StorageTransactionsFile;

public class MainUtilRecoverStorageFiles
{
	static final String PATH_CORRUPTED =
		"D:/_Allianz/2019-03-14_ProdDb/20190313_2330_autobackup_prod_kaputt/backup_daily_2019-03-13Z"
	;
	static final long
		LENGTH_LOWER_VALUE   = Binary.entityHeaderLength()       ,
		LENGTH_UPPER_BOUND   = 100_000                           ,
		TYPEID_LOWER_VALUE   = 10                                ,
		TYPEID_UPPER_BOUND   = 1013002                           ,
		OBJECTID_LOWER_VALUE = Persistence.defaultStartObjectId(),
		OBJECTID_UPPER_BOUND = 1000000000001000000L
	;
	
	public static void main(final String[] args) throws Exception
	{
		rollbackTransfers();
//		validateDataFiles();
//		printTransactionsFile();
	}
//	1000000000000087529
	static void validateDataFiles()
	{
		final StorageFileProvider sfp = StorageFileProvider
			.Builder()
			.setBaseDirectory(PATH_CORRUPTED)
			.createFileProvider()
		;
		final BulkList<StorageNumberedFile> storageFiles = sfp.collectDataFiles(BulkList.New(), 0);
		
		final StorageDataFileValidator dfv = StorageDataFileValidator.DebugLogging(
			BinaryEntityRawDataIterator.New(),
			StorageEntityDataValidator.DebugLogging(
				StorageEntityDataValidator.New(
					LENGTH_LOWER_VALUE  , LENGTH_UPPER_BOUND  ,
					TYPEID_LOWER_VALUE  , TYPEID_UPPER_BOUND  ,
					OBJECTID_LOWER_VALUE, OBJECTID_UPPER_BOUND
				)
			),
			StorageFileEntityDataIterator.New()
		);
		
		for(final StorageNumberedFile file : storageFiles)
		{
			dfv.validateFile(file);
		}
	}
	
	static void printTransactionsFile()
	{
		final StorageTransactionsFile tf = StorageTransactionsFile.parseFile(
			new File(PATH_CORRUPTED + "/channel_0/transactions_0.sft")
		);
		tf.entries().iterate(System.out::println);
	}
	
	static void rollbackTransfers() throws Exception
	{
		final File sourceFile = new File(PATH_CORRUPTED + "/channel_0/channel_0_872.dat");
				
		final File dir = XFiles.ensureDirectory(new File("D:/_Allianz/20190313_2330_Rollback"));
		XDebug.deleteAllFiles(dir, false);
		
		final StorageRollbacker sr = new StorageRollbacker(
			863,
			EqHashTable.New(KeyValue(872L, sourceFile)),
			dir, "rolledBack_", "storesOnly_",
			new StorageRollbacker.EntityDataHeaderEvaluator(
				LENGTH_LOWER_VALUE  , LENGTH_UPPER_BOUND  ,
				TYPEID_LOWER_VALUE  , TYPEID_UPPER_BOUND  ,
				OBJECTID_LOWER_VALUE, OBJECTID_UPPER_BOUND
			)
		);

		final StorageTransactionsFile tf = StorageTransactionsFile.parseFile(
			new File(PATH_CORRUPTED + "/channel_0/transactions_0.sft")
		);
//		sr.rollbackTransfers(tf);
		
//		sr.cleanUpDirect();
		sr.recoverStringsAndPrint();
	}
	
}