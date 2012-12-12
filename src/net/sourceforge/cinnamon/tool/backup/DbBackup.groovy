/**
 * Note: the backup package is currently not operational; this is work in [glacial] progress.
 */
package net.sourceforge.cinnamon.tool.backup

import groovy.sql.Sql
//import org.codehaus.groovy.grails.commons.ApplicationAttributes // commented out: no longer in a Grails context
//import org.codehaus.groovy.grails.web.context.ServletContextHolder
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Goal: Create a Cinnamon Backup via ssh, cygwin and 7zip on a MSSQL-Server 2000 on Windows 2000.<br/>
 * Notes:
 * <ul>
 * <li>The original zip file format only permits 65536 files. A normal Cinnamon install may have much more than this,
 * but your mileage may vary.</li>
 * <li>tar on cygwin may encounter serious problems while creating an archive - it croaks on some files
 * and folders with a spurious "file changed" error. So you can either ignore all read errors (not
 * good) or use another tool. If you are going to use this class on Linux, tar may very well be a good choice.
 * </li>
 * <li>7-zip does not like relative filenames, so saving to $foo.7z is not working in our
 * experience. Use "C:/$foo.7z". Oh, and it may completely ignore cygwins file system... so no /cygdrive/c.</li>
 * <li>The "tar*"-part is probably not working at the moment. Debug to your heart's content.</li>
 * </ul>
 *  
 * @author Stefan Rother, Ingo Wiarda
 *
 */
public class DbBackup{
	private transient Logger log = LoggerFactory.getLogger(this.getClass());

	def today = String.format('%tF', Calendar.getInstance())
	def tempDir = System.getProperty('java.io.tmpdir')
	AntBuilder ant = new AntBuilder()

	private def getDataSourceForEnv() {
	        def servletContext = org.codehaus.groovy.grails.web.context.ServletContextHolder.servletContext
	        def ctx = servletContext
	                  .getAttribute(org.codehaus.groovy.grails.commons.ApplicationAttributes.APPLICATION_CONTEXT)
	        return ctx.dataSource
	}

	void createRemoteDbBackup(def sql, def dbname, def backupfilename) {
		sql.execute("BACKUP DATABASE ? TO DISK=?", [dbname, backupfilename.toString()])
		log.debug "backup of '$dbname' created as '$backupfilename'"
	}

	void zipRemoteBackups(def host, def dbname, def archivename, def backupfilename, def custombackupfilename, def dbconnection) {
		def config = org.codehaus.groovy.grails.commons.ConfigurationHolder.config
		def dbHost = config.dbhosts[dbconnection.dbhost]
		log.debug("dbHost: ${dbHost} / dbHost.ssh: ${dbHost?.ssh} / dbConnection: ${dbconnection}")
		def sshUsername = dbconnection.sshusername ?: dbHost.ssh.username
		def sshPassphrase = dbconnection.sshpassphrase ?: dbHost.ssh.passphrase
		def metadatadir = dbconnection.metadatadir ?: dbHost.metadatadir
		def zip = "7z" 
		ant.sshexec( host : host,
					username : sshUsername,
					password : sshPassphrase,
					command : "$zip a -t7z C:/$archivename $backupfilename $custombackupfilename && " +
								"cd ${metadatadir} && $zip a -t7z C:/$archivename ${dbname}",
					trust : true, // TODO: klären, ob der Key vorher importiert werden muss
		)
		sleep(3000); // sleep for 3 seconds in case 7zip has not already copied / renamed the zip file.
	}
	
	void tarRemoteBackups(def host, def dbname, def archivename, def backupfilename, def custombackupfilename, def dbconnection) {
		def config = org.codehaus.groovy.grails.commons.ConfigurationHolder.config
		def dbHost = config.dbhosts[dbconnection.dbhost]
		log.debug("dbHost: ${dbHost} / dbHost.ssh: ${dbHost?.ssh} / dbConnection: ${dbconnection}")
		def sshUsername = dbconnection.sshusername ?: dbHost.ssh.username
		def sshPassphrase = dbconnection.sshpassphrase ?: dbHost.ssh.passphrase
		def metadatadir = dbconnection.metadatadir ?: dbHost.metadatadir

		def cmds = ["tar cvf $archivename $backupfilename $custombackupfilename",
		            "cd ${metadatadir} && tar rvf $archivename ${dbname}",
		            "gzip -v9 ~/$archivename"
		            ]
		cmds.each{command ->
			sshexec(host, sshUsername, sshPassphrase, command)
		}

		log.debug("finished tarring.")
	}
	
	void sshexec( host, username, password, command, ignoreError){
		log.debug("sshexec: $command")
		ant.sshexec(host:host,username:username, 
				password:password, command:command, 
				trust:true)
	}
	 
	void transferFileFromHost(def host, def file, def dbconnection) {
		log.debug "transfer backup file '${file}' to client"
		
		def config = org.codehaus.groovy.grails.commons.ConfigurationHolder.config
		def dbHost = config.dbhosts[dbconnection.dbhost]
		def sshUsername = dbconnection.sshusername ?: dbHost.ssh.username
		def sshPassphrase = dbconnection.sshpassphrase ?: dbHost.ssh.passphrase
		def remoteFile = "${sshUsername}@$host:$file"
		log.debug("scp command: ${remoteFile}")
		ant.scp(remotefile : remoteFile,
		   		password : sshPassphrase,
		   		localtodir : tempDir,
		   		verbose : true,
		   		trust : true,
		   		)
		log.debug "backup transferred"
	}

	void removeRemoteFiles(def host, def filenames, def dbconnection) {
		def config = org.codehaus.groovy.grails.commons.ConfigurationHolder.config
		def dbHost = config.dbhosts[dbconnection.dbhost]
		def sshUsername = dbconnection.sshusername ?: dbHost.ssh.username
		def sshPassphrase = dbconnection.sshpassphrase ?: dbHost.ssh.passphrase
		ant.sshexec( host : host,
					username : sshUsername,
					password : sshPassphrase,
					command : "rm ${filenames.join(' ')}",
					trust : true, // TODO: klären, ob der Key vorher importiert werden muss
		)
	}
	
	/*
	 * create the backups
	 */
	String createBackup() {

		// TODO: DB-Typ ist MS SQL Server?
		log.debug "create the backups"

		def ds = getDataSourceForEnv()
        def con = ds.getConnection()
        
        // check DB server name and version
        if (con.databaseProductName != 'Microsoft SQL Server' ||
        		con.databaseMajorVersion < 8) {
        	throw new Exception('This version of the backup needs at least MS SQL Server 8.0')
        }
        
        def host = con.serverName
		
		def sql = new Sql(con)
        def dbname = con.databaseName
        
		def config = org.codehaus.groovy.grails.commons.ConfigurationHolder.config
//		log.debug("config.hibernate:${config.hibernate}")
//		
//		log.debug("config::\n${config}")
//		log.debug("dbhosts::\n${config.dbHosts}\ndbHosts:${config.dbhosts}")
//		log.debug("con:${con.dump()}")
	
        def dbconnection = config.dbconnections[dbname]
//        log.debug("dbconnection:${dbconnection}")
		try {
			def backupfilename = "C:/${dbname}_backup"
			def customdb = dbname + '_c'
			def custombackupfilename = "C:/${customdb}_backup"
			createRemoteDbBackup(sql, dbname, backupfilename)
			createRemoteDbBackup(sql, customdb, custombackupfilename)

			log.debug "archive backups"
			def archivename = "dbbackup-${dbname}-${today}.zip"
			def packedArchivename = "C:/${archivename}"  //+ ".gz"
			
			zipRemoteBackups(host, dbname, archivename, backupfilename, custombackupfilename, dbconnection)
			transferFileFromHost(host, packedArchivename, dbconnection)

			removeRemoteFiles(host, [packedArchivename, backupfilename, custombackupfilename], dbconnection)
			
			return new File(tempDir, archivename).absolutePath
		} catch(Exception e) {
			log.debug "", e
			throw e    
		} finally {
			sql.close()
		}
	
	}
	
}
