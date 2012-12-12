/**
 * Note: the backup package is currently not operational; this is work in [glacial] progress.
 */
package net.sourceforge.cinnamon.tool.backup

//import org.apache.ddlutils.PlatformFactory;
//import org.apache.ddlutils.io.DatabaseDataIO;
//import org.apache.ddlutils.io.DatabaseIO;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;

//import org.codehaus.groovy.grails.commons.ApplicationAttributes;


import java.util.zip.GZIPOutputStream;

//import org.codehaus.groovy.grails.web.context.ServletContextHolder
//import grails.plugins.springsecurity.Secured;

//@grails.plugins.springsecurity.Secured(["hasRole('_superusers')"])
class DbBackupController {

    def index = { }
    
    def createBackup = {
        def filename
        try {
            filename = new net.sourceforge.cinnamon.tool.backup.DbBackup().createBackup()
            log.debug 'send file to client'
            sendFileToClient(new File(filename))

            flash.message = (filename == null) ? '' : message(code: 'backup.success', args: [filename])
        } catch (Exception e) {
            log.debug "", e
            flash.error = e.message
            return redirect(controller: 'security', action: 'index')
        }
        return [filename: filename]
    }

    void sendFileToClient(File file) {
    	log.debug "read bytes from file '${file.absolutePath}'"
		response.contentType = "application/octet-stream"
		response.setHeader("Content-disposition", "attachment; filename=${file.name}")
		
    	// WORKAROUND: if the backup file is too large, then file.readBytes() will evoke an OutOfMemoryError
    	// so read it MB wise
    	if (file.size() > 16 * 1024 * 1024) {
	    	byte[] bytes = new byte[1024 * 1024] // 1MB buffer
			int i = 0
			file.withInputStream() { is ->
				while ((i = is.read(bytes)) != -1) { // -1 : no more bytes were read
					response.outputStream.write(bytes, 0, i)
				}
			}
    	} else {
    		response.outputStream << file.readBytes()
    	}
	}

	private def getDataSourceForEnv(env) {
        def servletContext = ServletContextHolder.servletContext
        def ctx = servletContext
                  .getAttribute(ApplicationAttributes.APPLICATION_CONTEXT)
        return ctx.dataSource
	}

    def createDdlUtilsBackup = {
		log.debug 'createDdlUtilsBackup'
		def ds = getDataSourceForEnv()
		log.debug "ds = ${ds.dump()}"

		def env = EnvironmentHolder.getEnvironment()
		println "env = $env"
		def dbname = env.dbname
		println  "dbname = $dbname"

		def platform = PlatformFactory.createNewPlatformInstance(ds)
		def modelReader = platform.getModelReader()
		def connection = platform.dataSource.connection
		def db = modelReader.getDatabase(connection, dbname)
		def dbCustom = modelReader.getDatabase(connection, dbname + '_c')
		
		def config = org.codehaus.groovy.grails.commons.ConfigurationHolder.config
		log.debug "config = $config"
		
		log.debug "db = ${db.dump()}"
		log.debug "db = ${db.toVerboseString()}"

		def today = String.format('%tF', Calendar.getInstance())
		def tempDir = System.getProperty('java.io.tmpdir')
		def backupName = "adminToolBackup-${dbname}-${today}"
		def backupPath = "$tempDir/$backupName"
		new File(backupPath).mkdir()
							
		// write DB model to XML
		println 'write DB model to XML'
//		new DatabaseIO().write(db, "$backupPath/dbModel.xml")
//		new DatabaseIO().write(dbCustom, "$backupPath/dbModel_c.xml")
		
		// write DB data to XML
		println 'write DB data to XML'
//		new DatabaseDataIO().writeDataToXML(platform, db, "$backupPath/dbData.xml", 'UTF-8')
//		new DatabaseDataIO().writeDataToXML(platform, dbCustom, "$backupPath/dbData_c.xml", 'UTF-8')

		// backup OSD content
		backupContent(backupPath)

		compressBackup(tempDir, backupName)

		[:]
    }

    // backup OSD content into backupDir
    private void backupContent(def backupDir) {
    	log.debug 'backupContent'

    	String cinnamonHomeDir = System.env.CINNAMON_HOME_DIR
    	println "cinnamonHomeDir = '$cinnamonHomeDir'"

		def env = EnvironmentHolder.getEnvironment()
		println "env.metadatadir = ${env.metadatadir}"
		println "env.dbname = ${env.dbname}"
		def contentPath = "${env.metadatadir}/${env.dbname}"

    	new File(contentPath).eachFileRecurse { File file ->
    		println "file = $file"
    		println "file.isFile = ${file.isFile()}"
    		if (file.isFile()) {
    			file.withInputStream { is ->
    				new File(backupDir, file.name).newOutputStream() << is
    			}
    			 
    		}
    	}
		    	
    }

    private void compressBackup(def backupDir, def backupName) {
		response.contentType = "application/octet-stream"
		response.setHeader("Content-disposition", "attachment; filename=${backupName + '.tar.gz'}")
			
//		TarOutputStream out = new TarOutputStream(new GZIPOutputStream(new File(backupPath + '.tar.gz').newOutputStream()))
		TarOutputStream out = new TarOutputStream(new GZIPOutputStream(response.outputStream))
		byte[] data = new byte[16 * 1024 * 1024]

		// get a list of files from current directory
		new File(backupDir + '/' + backupName).eachFile { file ->
			TarEntry entry = new TarEntry(file)
			entry.name = file.name
			out.putNextEntry(entry)
			
			int count = 0
			file.withInputStream() { is ->
				while ((count = is.read(data)) != -1) { // -1 : no more bytes were read
					out.write(data, 0, count)
				}
				out.closeEntry()
			}
		}
		out.close();
    }
}
