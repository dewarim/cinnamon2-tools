package net.sourceforge.cinnamon.tool.converter

import net.sourceforge.cinnamon.tool.HibernateConnector;
import net.sourceforge.cinnamon.tool.PropertiesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Folder;
import server.dao.DAOFactory;
import server.dao.ObjectSystemDataDAO;
import server.data.ObjectSystemData;
import server.global.Conf;
import utils.HibernateSession;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import java.io.File;
import java.util.List;
import java.util.Properties;

/*
 * Convert all OSDs and Folders with metadata to use the new metaset api.
 * 
 * <br/>
 * <strong>
 * This class does not support the dryRun-Parameter like other converters.
 * </strong><br/>
 * Usage:
 *  java -Xms700M -Xmx 700M -ea -jar metaConverter.jar path_to_metadataConverter.properties [startRow of result]
 * Parameters:
 *  1. path to config file
 *  2. (optional) start row of result set.
 *      (If the process croaks for some reason, you can start again without going through all
  *      already converted items again).
 *
 * Configuration:
 * MetadataConverter requires two configuration files:
 * 1. cinnamon_config.xml in CINNAMON_HOME_DIR, 
 * 2. config.properties file, the path to which is specified on the command line.
 *   fields:
 *      server.url=http://cinnamon.test:8080/cinnamon/cinnamon
 *      server.password=admin
 *      server.username=admin
 *      default_repository=cmn_test
 */

public class MetaConverter {

    private Logger log = LoggerFactory.getLogger(this.getClass());
    Conf cinnamonConfig;
    EntityManager em;
    protected Properties config = new Properties();

    String repository;

    Integer firstResult = 0;
    Integer lastResult = 58949;

    public MetadataConverter() {
    }

    public void setUp(String propertiesFilename) {
        assert new File(propertiesFilename).exists(): "path to config.properties was not found.";
        config = PropertiesLoader.load(new File(propertiesFilename));

        /*
        * this method contains code from HibernateSession for debugging
        * purposes. If in doubt, refactor.
        */
        cinnamonConfig = new Conf("cinnamon_config.xml");
        repository = config.getProperty("default_repository");
        em = HibernateConnector.connect(cinnamonConfig, repository);
        HibernateSession.setLocalEntityManager(em);
    }

    @SuppressWarnings("unchecked")
    public void convert() {
        int rowCount = 1;
        try {
            log.debug("Starting conversion.");            
//            Query query = em.createQuery("select o.id from ObjectSystemData o where datalength(o.metadata) > 9 and o.metadata not like '%<meta />'");
//            def osdIds = query.getResultList();
            DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
            ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
            def total = lastResult - firstResult
            (firstResult..lastResult).each { Long id ->
                try {
                    System.err.println("Working on OSD ${id}: row: ${rowCount++} / $total ")
                    log.debug("Working on OSD ${id}: row: ${rowCount} / $total ")
                    def et = em.transaction
                    et.begin()
                    ObjectSystemData osd = oDao.get(id)
                    if(osd == null){                        
                        log.debug("no OSD#${id} == nop")
                    }
                    else{
                        osd.setMetadata(osd.getMetadata())
                    }
                    et.commit()
                } catch (Exception e) {
                    log.error("failed to convert osd #${id}", e)
                    throw new RuntimeException(e)
                }
                log.debug("convert OSD #${id}")
            }

            try {
                def folders = em.createQuery("select f.id, f.metadata from Folder f where datalength(f.metadata) > 9 and f .metadata not like '%<meta />'").resultList
                log.debug("found: ${folders.size()} Folders")
                def folderDao = daoFactory.getFolderDAO(em)
                total = folders.size()
                rowCount = 0
                folders.each { row ->
                    System.err.println("Working on Folder ${row[0]}: row: ${rowCount++} / $total ")
                    log.debug("convert Folder #${row[0]}")
                    def et = em.transaction
                    et.begin()
                    Folder folder = folderDao.get(row[0])
                    folder.setMetadata(row[1])
                    et.commit()
                }
                                
                def et = em.getTransaction()
                et.begin()
                def query = em.createQuery("update ObjectSystemData o set o.metadata=:meta")
                query.setParameter('meta','<meta />')
                def osdUpdates = query.firstResult
                query =  em.createQuery("update Folder f set f.metadata=:meta ")
                query.setParameter('meta', '<meta />')
                def folderUpdates =query.firstResult
                log.debug("updated ${osdUpdates} objects and ${folderUpdates} folders");
                log.debug("finished migration.")
                et.commit()
                println("finished migration.")
            }
            catch (Throwable t) {
                log.error("failed to migrate: ",t)
            }

        }
        catch (Exception e) {
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    public static void main(String[] args) {
        System.out.println("MetaConverter.");

        MetaConverter mc = new MetaConverter();
        if (args.length == 1) {
            mc.setUp(args[0]);
            mc.convert();
        } else if (args.length == 3) {
            mc.setUp(args[0]);
            mc.firstResult = Integer.parseInt(args[1]);
            mc.lastResult = Integer.parseInt(args[2])
            mc.convert();
        } else {
            System.out
                    .println("Usage: java -Xms800M -Xmx 800M -ea -Dlogback.configurationFile=logback.xml -jar metaConverter.jar path_to_config.properties startId endId");
        }
        System.out.println("Done.");
    }

}
