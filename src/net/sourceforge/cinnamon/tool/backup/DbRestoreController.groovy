/**
 * Note: the backup package is currently not operational; this is work in [glacial] progress.
 */
package net.sourceforge.cinnamon.tool.backup

//import org.apache.ddlutils.Platform;
//import org.apache.ddlutils.PlatformFactory;
//import org.codehaus.groovy.grails.commons.ApplicationAttributes;
//import org.codehaus.groovy.grails.web.context.ServletContextHolder;
import org.apache.tools.tar.TarEntry;
//import org.apache.ddlutils.model.Database;
import org.apache.tools.tar.TarInputStream;
//import org.apache.ddlutils.PlatformUtils;


//import org.apache.ddlutils.io.DatabaseDataIO;
import java.util.zip.GZIPInputStream;

//import org.apache.ddlutils.io.DatabaseIO
//import grails.plugins.springsecurity.Secured;

//@Secured(["hasRole('_superusers')"])
class DbRestoreController {

	def env = EnvironmentHolder.getEnvironment()
	def tempDir = System.getProperty('java.io.tmpdir')

    def index = { }

    void writeBackup(def tmpFile, File backupFile) {
    	// WORKAROUND: if the temp file is too large, then file.getBytes() will evoke an OutOfMemoryError
    	// so read it MB wise
		backupFile.withOutputStream() { fos ->
	    	if (tmpFile.size > 16 * 1024 * 1024) {
	    		byte[] bytes = new byte[1024 * 1024] // 1MB buffer
	    		InputStream is = tmpFile.getInputStream()
	    		int i
	    		while ((i = is.read(bytes)) != -1) {
	    			fos.write(bytes, 0, i)
	    		}
	    	} else {
	    		fos << tmpFile.getBytes()
	    	}
		}

    }

    def restoreBackup = {
    		try {
				def tmpFile = request.getFile('backupFile')
				log.debug "tmpFile: name='${tmpFile.name}' size=${tmpFile.size}"
				
				def backupFile = File.createTempFile('dbbackup', '.zip')
				log.debug "writing backup to '${backupFile.name}'"
				writeBackup(tmpFile, backupFile)
	    		
	    		new net.sourceforge.cinnamon.tool.backup.DbRestore().restoreBackup(backupFile.absolutePath)
	    		flash.message = message(code:'backup.restore.success')
    		} catch (Exception e) {
    			log.debug '', e 
    			flash.error = e.message
    		}
			redirect(controller:'security', action:'index')
    }
    
    def show = {
    		render(view:"show")
    }

	private def getDataSourceForEnv(env) {
        def servletContext = ServletContextHolder.servletContext
        def ctx = servletContext
                  .getAttribute(ApplicationAttributes.APPLICATION_CONTEXT)
        return ctx.dataSource
	}

    def showRestoreDdlUtilsBackup = {
   		render(view:"showRestoreDdlUtilsBackup")
    }

//    def restoreDdlUtilsBackup = {
//		def contentPathName = "${env.metadatadir}/${env.dbname}"
//		File contentPath = new File(contentPathName)
//
//		if (new File(contentPath.absolutePath + '.admintool.backup').exists()) {
//			flash.error = 'error.restore.backup_path_exists'
//			redirect(uri:"/security/index")
//			return
//		}
//
//		// decompress backup
//		def tmpFile = request.getFile('backupFile')
//		def backupPath = "$tempDir/${tmpFile.name}"
//		decompressBackup(backupPath, tmpFile)
//
//		def ds = getDataSourceForEnv()
//		Platform platform = PlatformFactory.createNewPlatformInstance(ds)
//		platform.identityOverrideOn = true // preserve PK IDs
//
//		def dbType = new PlatformUtils().determineDatabaseType(ds)
//		log.debug "dbType = $dbType"
//
//		// restore model
//		log.debug 'restore model'
//		boolean continueOnErrors = false
//
//		def dbname = env.dbname
//		def modelReader = platform.getModelReader()
//		def connection = platform.dataSource.connection
//		Database db = modelReader.getDatabase(connection, dbname)
//
//
//
////		platform.dropTables(db, continueOnErrors)
//
///*
//		// WORKAROUND: add missing sequences
//		// DdlUtils tries to DROP inexisting sequences
//		if (dbType == 'PostgreSql') {
////			platform.evaluateBatch(
//////					'CREATE SEQUENCE users_id_seq;' +
////					'CREATE SEQUENCE sessions_id_seq;' +
////					'CREATE SEQUENCE relationtypes_id_seq;' +
////					'CREATE SEQUENCE relations_id_seq;' +
////					'CREATE SEQUENCE permissions_id_seq;' +
////					'CREATE SEQUENCE objtypes_id_seq;' +
////					'CREATE SEQUENCE objects_id_seq;' +
////					'CREATE SEQUENCE messages_id_seq;' +
////					'CREATE SEQUENCE languages_id_seq;' +
////					'CREATE SEQUENCE index_types_id_seq;' +
////					'CREATE SEQUENCE index_items_id_seq;' +
////					'CREATE SEQUENCE index_groups_id_seq;' +
////					'CREATE SEQUENCE groups_id_seq;' +
////					'CREATE SEQUENCE group_users_id_seq;' +
////					'CREATE SEQUENCE formats_id_seq;' +
////					'CREATE SEQUENCE folders_id_seq;' +
////					'CREATE SEQUENCE folder_types_id_seq;' +
//////					'CREATE SEQUENCE aclentry_permissions_id_seq;'
////					'CREATE SEQUENCE customtables_id_seq;' +
////					'CREATE SEQUENCE acls_id_seq;'
////					, continueOnErrors)
//			def tables = db.getTables()
//			Iterator it = platform.query(database,
//                    "select * from book where title = ?",
//                    params,
//                    new Table[] { database.findTable("book") });
//
//		}
//*/
//
//		def modelPath = "$backupPath/dbModel.xml"
////		def newModel = new DatabaseIO().read(modelPath)
//		def newModel = new DatabaseIO().read(new File(modelPath).toURL().toExternalForm())
//		boolean dropTablesFirst = true
////		boolean dropTablesFirst = false
//		platform.createTables(newModel, dropTablesFirst, continueOnErrors) // drop tables first, don't continue und errors
//
//		// restore data
//
//		// WORKAROUND: turn constraint checks off for some tables for the time
//		// of the data restore since the self reference of the root folder causes
//		// trouble in MS SQL Server in certain cases.
//		// (INSERT statement conflicted with COLUMN FOREIGN KEY SAME TABLE constraint 'FKD74671C551710E69'. The conflict occurred in database 'foo', table 'folders', column 'id'.)
//		// (INSERT statement conflicted with COLUMN FOREIGN KEY constraint 'FK5A6C3CC64E6FDACF'. The conflict occurred in database 'foo', table 'groups', column 'id'.)
//		// (INSERT statement conflicted with COLUMN FOREIGN KEY constraint 'FK41CACC484E6FDACF'. The conflict occurred in database 'foo', table 'groups', column 'id'.)
//		// (INSERT statement conflicted with COLUMN FOREIGN KEY constraint 'FK41CACC48C74369C5'. The conflict occurred in database 'foo', table 'users', column 'id'.)
//		// (INSERT statement conflicted with COLUMN neeFOREIGN KEY constraint 'FK9D13C51451710E69'. The conflict occurred in database 'foo', table 'folders', column 'id'.)
//		// (INSERT statement conflicted with COLUMN FOREIGN KEY SAME TABLE constraint 'FK9D13C514BF66629'. The conflict occurred in database 'foo', table 'objects', column 'id'.)
//		// (INSERT statement conflicted with COLUMN FOREIGN KEY constraint 'FK9D13C51426408304'. The conflict occurred in database 'foo', table 'objtypes', column 'id'.)
//		// (INSERT statement conflicted with COLUMN FOREIGN KEY SAME TABLE constraint 'FK9D13C5147F4850C8'. The conflict occurred in database 'foo', table 'objects', column 'id'.)
//		// (INSERT statement conflicted with COLUMN FOREIGN KEY constraint 'FK_metadataindex_objects'. The conflict occurred in database 'foo', table 'objects', column 'id'.)
//		// (INSERT statement conflicted with COLUMN FOREIGN KEY constraint 'FKFF8B45F777BE7FF3'. The conflict occurred in database 'cmn_MBBras_XML', table 'objects', column 'id'.)
//		// (INSERT statement conflicted with COLUMN FOREIGN KEY constraint 'FKFF8B45F714370C8'. The conflict occurred in database 'cmn_MBBras_XML', table 'objects', column 'id'.)
//		if (dbType == 'MsSql') { // MS SQL Server 2000 = MsSql
//			platform.evaluateBatch(
////					'ALTER TABLE folders     NOCHECK CONSTRAINT FKD74671C551710E69;' +
////					'ALTER TABLE aclentries  NOCHECK CONSTRAINT FK5A6C3CC64E6FDACF;' +
////					'ALTER TABLE group_users NOCHECK CONSTRAINT FK41CACC484E6FDACF;' +
////					'ALTER TABLE group_users NOCHECK CONSTRAINT FK41CACC48C74369C5;' +
////					'ALTER TABLE objects     NOCHECK CONSTRAINT FK9D13C51451710E69;' +
////					'ALTER TABLE objects     NOCHECK CONSTRAINT FK9D13C514BF66629;' +
////					'ALTER TABLE objects     NOCHECK CONSTRAINT FK9D13C51426408304;' +
////					'ALTER TABLE objects     NOCHECK CONSTRAINT FK9D13C5147F4850C8;' +
////					'ALTER TABLE metadataindex NOCHECK CONSTRAINT FK_metadataindex_objects;' +
////					'ALTER TABLE relations NOCHECK CONSTRAINT FKFF8B45F777BE7FF3;' +
////					'ALTER TABLE relations NOCHECK CONSTRAINT FKFF8B45F714370C8;'
//					'ALTER TABLE folders     NOCHECK CONSTRAINT ALL;' +
//					'ALTER TABLE aclentries  NOCHECK CONSTRAINT ALL;' +
//					'ALTER TABLE group_users NOCHECK CONSTRAINT ALL;' +
//					'ALTER TABLE objects     NOCHECK CONSTRAINT ALL;' +
//					'ALTER TABLE relations   NOCHECK CONSTRAINT ALL;' +
//					'ALTER TABLE aclentry_permissions NOCHECK CONSTRAINT ALL;' +
//					'ALTER TABLE index_types NOCHECK CONSTRAINT ALL;' +
//					'ALTER TABLE index_items NOCHECK CONSTRAINT ALL;'
//					, false)
//		}
//		try {
//			log.debug 'restore data'
//			String[] files = ["file:///$backupPath/dbData.xml"]
//	        DatabaseDataIO dbIO = new DatabaseDataIO()
//			dbIO.useBatchMode = true
//			dbIO.ensureFKOrder = false
//			dbIO.writeDataToDatabase(platform, files)
//		} catch (Exception ex) {
//			println "ex = $ex"
//			throw ex
//		} finally {
//			if (dbType == 'MsSql') {
//				platform.evaluateBatch(
////						'ALTER TABLE relations   CHECK CONSTRAINT FKFF8B45F714370C8;' +
////						'ALTER TABLE relations   CHECK CONSTRAINT FKFF8B45F777BE7FF3;' +
////						'ALTER TABLE metadataindex CHECK CONSTRAINT FK_metadataindex_objects;' +
////						'ALTER TABLE objects     CHECK CONSTRAINT FK9D13C5147F4850C8;' +
////						'ALTER TABLE objects     CHECK CONSTRAINT FK9D13C51426408304;' +
////						'ALTER TABLE objects     CHECK CONSTRAINT FK9D13C514BF66629;' +
////						'ALTER TABLE objects     CHECK CONSTRAINT FK9D13C51451710E69;' +
////						'ALTER TABLE group_users CHECK CONSTRAINT FK41CACC48C74369C5;' +
////						'ALTER TABLE group_users CHECK CONSTRAINT FK41CACC484E6FDACF;' +
////						'ALTER TABLE aclentries  CHECK CONSTRAINT FK5A6C3CC64E6FDACF;' +
////						'ALTER TABLE folders     CHECK CONSTRAINT FKD74671C551710E69;', false)
//						'ALTER TABLE index_types CHECK CONSTRAINT ALL;' +
//						'ALTER TABLE index_items CHECK CONSTRAINT ALL;' +
//						'ALTER TABLE aclentry_permissions CHECK CONSTRAINT ALL;' +
//						'ALTER TABLE relations   CHECK CONSTRAINT ALL;' +
//						'ALTER TABLE objects     CHECK CONSTRAINT ALL;' +
//						'ALTER TABLE group_users CHECK CONSTRAINT ALL;' +
//						'ALTER TABLE aclentries  CHECK CONSTRAINT ALL;' +
//						'ALTER TABLE folders     CHECK CONSTRAINT ALL;', false)
//			}
//		}
//
//		// restore OSD content
//		restoreContent(backupPath)
//
//		flash.message = 'restore.successful'
//
//		redirect(uri:"/security/index")
//    }

    def decompressBackup(def backupPath, def tmpFile) {
    	log.debug 'decompress backup'
		new File(backupPath).mkdir()
		def fis = tmpFile.getInputStream()
		try {
			def gzIn = new GZIPInputStream(fis)
			def tarIn = new TarInputStream(gzIn)
			
			TarEntry tarEntry
			while (tarEntry = tarIn.getNextEntry()) {
				println "untarring ${tarEntry.name}"
				byte[] bytes = new byte[1024 * 1024]
                int i = 0
                new File(backupPath + '/' + tarEntry.name).withOutputStream() { os ->
	                while ((i = tarIn.read(bytes)) != -1) { // -1 : no more bytes were read
	                	os.write(bytes, 0, i)
	                }
				}
			}
		} finally {
			fis.close()
		}
    }

    def getFormats(def platform, def db) {
    	println 'resolve format IDs to names'
		def formats = [:]
		platform.query(db, 'SELECT * FROM formats').each { record ->
			formats[record['id']] = record['name']
		}
    	return formats
    }

	// restore OSD content
	private void restoreContent(def backupDir) {
		log.debug 'restore content'
	
		def contentPathName = "${env.metadatadir}/${env.dbname}"

		File contentPath = new File(contentPathName)

		// rename old content directory
		log.debug "contentPath = $contentPath"
		log.debug "contentPath.exists() = ${contentPath.exists()}"
		if (contentPath.exists()) {
			assert contentPath.renameTo(new File(contentPath.getAbsolutePath() + '.admintool.backup'))
		}

		new File(backupDir).list().each { fileName ->
			if (fileName != 'dbModel.xml' && fileName != 'dbData.xml') {
				// create the directory levels for the content file
				def firstLevelDirName = fileName[0..1]
				def firstLevelDir = new File(contentPath, firstLevelDirName)

				def secondLevelDirName = fileName[2..3]
  				def secondLevelDir = new File(firstLevelDir, secondLevelDirName)
				
				def thirdLevelDirName = fileName[4..5]
 				File thirdLevelDir = new File(secondLevelDir, thirdLevelDirName)

				if (!thirdLevelDir.exists()) {
					assert thirdLevelDir.mkdirs()
				}
				
				// move content file to content directory
				assert new File(backupDir, fileName).renameTo(new File(thirdLevelDir, fileName))
			}
		}
	}	
}
