/**
 * Note: the backup package is currently not operational; this is work in [glacial] progress.
 */
package net.sourceforge.cinnamon.tool.backup

import groovy.sql.Sql
//import org.codehaus.groovy.grails.commons.ApplicationAttributes
//import org.codehaus.groovy.grails.web.context.ServletContextHolder
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Stefan Rother
 *
 */
public class DbRestore{
	private transient Logger log = LoggerFactory.getLogger(this.getClass());

//	HARDCODED
	def backupDir = 'C:/'
	def cinnamonUserName = 'cinnamon'
	def indexerUserName = 'index'

	def today = String.format('%tF', Calendar.getInstance())
	def tempDir = System.getProperty('java.io.tmpdir')

	AntBuilder ant = new AntBuilder()

//	 TODO: DB-Typ ist MS SQL Server?

	private def getDataSourceForEnv() {
		def servletContext = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext
		def ctx = servletContext
			.getAttribute(org.codehaus.groovy.grails.commons.ApplicationAttributes.APPLICATION_CONTEXT)
			
		log.debug "servletContext.attributeNames = '${servletContext.attributeNames.dump()}'"
		return ctx.dataSource
	}

	private void transferFileToHost(def host, def file, def dbconnection) {
		log.debug "transfer '$file' to $host"
		def config = org.codehaus.groovy.grails.commons.ConfigurationHolder.config
		def dbHost = config.dbhosts[dbconnection.dbhost]
		def sshUsername = dbconnection.sshusername ?: dbHost.ssh.username
		def sshPassphrase = dbconnection.sshpassphrase ?: dbHost.ssh.passphrase

		ant.scp(file : file,
		   		remoteTodir : "${sshUsername}@$host:.",
				password : sshPassphrase,
		   		verbose : true,
		   		trust : true, // TODO: klären, ob der Key vorher importiert werden muss
	   		)
	}

	private void unzipRemoteBackups(def host, def dbname, def file, def backupfilename, def custombackupfilename, def dbconnection) {
		def config = org.codehaus.groovy.grails.commons.ConfigurationHolder.config
		def dbHost = config.dbhosts[dbconnection.dbhost]
		def sshUsername = dbconnection.sshusername ?: dbHost.ssh.username
		def sshPassphrase = dbconnection.sshpassphrase ?: dbHost.ssh.passphrase
		def metadatadir = dbconnection.metadatadir ?: dbHost.metadatadir
		if(metadatadir && dbname){
		ant.sshexec( host : host,
				username : sshUsername,
				password : sshPassphrase,
				command : "7z x ${file} && " +
					"rm -rf $metadatadir/${dbname} && mv -f ${dbname} $metadatadir && "+
					"mv $backupfilename $backupDir && mv $custombackupfilename $backupDir",
				trust : true, // TODO: klären, ob der Key vorher importiert werden muss
		)
		}
		else{
			throw new RuntimeException("empty var metadatadir or dbname")
		}
	}
	
	private void untarRemoteBackups(def host, String dbname, def file, def backupfilename, def custombackupfilename, def dbconnection) {
		def config = org.codehaus.groovy.grails.commons.ConfigurationHolder.config
		def dbHost = config.dbhosts[dbconnection.dbhost]
		def sshUsername = dbconnection.sshusername ?: dbHost.ssh.username
		def sshPassphrase = dbconnection.sshpassphrase ?: dbHost.ssh.passphrase
		def metadatadir = dbconnection.metadatadir ?: dbHost.metadatadir
		if(! (dbname?.length() > 0)){
			// prevent "rm -rf /"
			throw new RuntimeException("dbname is invalid. Canceling restore to prevent data loss.")
		}
		String command = "ls && tar xzf ${file}.zip && " +
		"rm -rf $metadatadir/${dbname} && mv -f ${dbname} $metadatadir && "+
		"mv -f $backupfilename $backupDir && mv -f $custombackupfilename $backupDir"
		log.debug("command to dbHost: \n${command}")
		ant.sshexec( host : host,
				username : sshUsername,
				password : sshPassphrase,
				command : command,
				trust : true, // TODO: klären, ob der Key vorher importiert werden muss
		)
		log.debug("untarring finished.")
	}

	private void restoreDatabase(def sql, def dbname, def backupfile) {
		log.debug "restoring DB '$dbname'"
		// Note: ALTER DATABASE and USE do not seem to accept '?' parameters or GString
		sql.execute("ALTER DATABASE " + dbname + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE")
		sql.execute("RESTORE DATABASE ? FROM DISK=?", [dbname, backupfile])
		sql.execute('ALTER DATABASE ' + dbname + ' SET MULTI_USER')
	}

	private void removeRemoteBackups(def host, def archiveName, def filenames, def dbconnection) {
		def config = org.codehaus.groovy.grails.commons.ConfigurationHolder.config
		def dbHost = config.dbhosts[dbconnection.dbhost]
		def sshUsername = dbconnection.sshusername ?: dbHost.ssh.username
		def sshPassphrase = dbconnection.sshpassphrase ?: dbHost.ssh.passphrase
		ant.sshexec( host : host,
					username : sshUsername,
					password : sshPassphrase,
					command : "rm ${archiveName} && rm ${filenames.join(' ')}",
					trust : true, // TODO: klären, ob der Key vorher importiert werden muss
		)
	}

	private Sql createSqlInstance(def host, def port, def dbconnection) {
	    def driverClassName = dbconnection.driverClassName
		def jdbcType = dbconnection.jdbcType
		def masterdbname = dbconnection.masterdbname
		def masterusername = dbconnection.masterusername
		def masterpassword = dbconnection.masterpassword
		Sql.newInstance("jdbc:$jdbcType://$host:$port/$masterdbname",
									masterusername, masterpassword, driverClassName)
	}

	private boolean dropUser(def sql, def username) {
		log.debug "delete user '$username'"
		// ab MS SQL Server 2005 gibt es CREATE USER und DROP USER
		sql.execute("EXEC sp_revokedbaccess @name_in_db=$username")
	}

	private boolean createUser(def sql, def username) {
		log.debug "creating user '$username'"
		// ab MS SQL Server 2005 gibt es CREATE USER und DROP USER
		def failure = sql.execute("EXEC sp_grantdbaccess $username, @name_in_db = $username")
		if (failure) {
			return failure
		}
		sql.execute("EXEC sp_changegroup 'db_owner', $username")
	}

	private void recreateUsers(def host, def port, def dbname, def dbconnection) {
		def sql = Sql.newInstance("jdbc:${dbconnection.jdbcType}://$host:$port/$dbname",
 				dbconnection.masterusername, dbconnection.masterpassword, dbconnection.driverClassName)
 		try {
			dropUser(sql, cinnamonUserName)
			dropUser(sql, indexerUserName)

			createUser(sql, cinnamonUserName)
			createUser(sql, indexerUserName)
		} catch(Exception e) {
			log.debug "", e
		} finally {
			sql.close()
		}
	}
	
	private void restoreDatabases(def host, def port, def dbname, def customdb, def backupfilename, def custombackupfilename, def archiveName, def dbconnection) {
		/*
		 * the RESTORE command must be run on master DB
		 * and the restored DB has to be in SINGLE_USER mode
		 * (see "Exclusive access could not be obtained because the database is in use"; http://social.msdn.microsoft.com/Forums/en-US/sqldisasterrecovery/thread/aad41cbb-10cb-4109-9e55-aab048bbeb9d)
		 */
		log.debug "establishing sql connection"
		def sql = createSqlInstance(host, port, dbconnection)
		log.debug 'restore DB'
		try {
			log.debug "dbname = '$dbname'"
			log.debug "backupfilename = '$backupfilename'"
			restoreDatabase(sql, dbname, "$backupDir/$backupfilename".toString())
			restoreDatabase(sql, customdb, "$backupDir/$custombackupfilename".toString())
	
			log.debug 'removing temp files'
			removeRemoteBackups(host, archiveName, ["$backupDir/$backupfilename", "$backupDir/$custombackupfilename"], dbconnection)
		} catch(Exception e) {
			   log.debug "", e
		} finally {
			sql.close()
		}
	}


	/*
	 * restore the backup
	 */
	void restoreBackup(def backupFilename) {
        log.debug "today is $today"
        
		def ds = getDataSourceForEnv()
        def con = ds.getConnection()

        // check DB server name and version
        if (con.databaseProductName != 'Microsoft SQL Server' ||
        		con.databaseMajorVersion < 8) {
        	throw new Exception('This version of the backup restore needs at least MS SQL Server 8.0')
        }

        def host = con.serverName
        def port = con.portNumber
        def dbname = con.databaseName
        	
		def config = org.codehaus.groovy.grails.commons.ConfigurationHolder.config
        def dbconnection = config.dbconnections[dbname]

		transferFileToHost(host, backupFilename, dbconnection)
		File backupFile = new File(backupFilename)
		def archiveName = backupFile.name //- '.zip'
		log.debug "archiveName = '$archiveName'"
	
		log.debug 'deflating archive'
		def backupfilename = "${dbname}_backup"
		def customdb = dbname + '_c'
		def custombackupfilename = "${customdb}_backup"
		unzipRemoteBackups(host, dbname, archiveName, backupfilename, custombackupfilename, dbconnection)
//		untarRemoteBackups(host, dbname, archiveName, backupfilename, custombackupfilename, dbconnection)
	
		restoreDatabases(host, port, dbname, customdb, backupfilename, custombackupfilename, archiveName, dbconnection)
		recreateUsers(host, port, dbname, dbconnection)
		recreateUsers(host, port, customdb, dbconnection)
		
	}	
}
