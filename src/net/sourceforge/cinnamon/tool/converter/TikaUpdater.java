package net.sourceforge.cinnamon.tool.converter;

import net.sourceforge.cinnamon.tool.HibernateConnector;
import net.sourceforge.cinnamon.tool.PropertiesLoader;
import org.dom4j.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Format;
import server.data.ObjectSystemData;
import server.tika.TikaParser;

import server.global.Conf;
import utils.HibernateSession;
import utils.ParamParser;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import java.io.File;
import java.util.List;
import java.util.Properties;

/*
 * Walk through all objects of a repository, and for all objects with content
 * trigger a re-parsing of the content by the TikaParser. The results will be added
 * to the object's custom metadata field. This currently affects _all_ objects with content,
 * at a later time this will be configurable through TikaConfig. (You can, of course,
 * use the objectType and format settings in the config.properties of TikaParser to
 * restrict parsing to individual combinations of objectType and Format.
 * <br/>
 * <strong>
 * This class does not support the dryRun-Parameter like other converters.
 * </strong><br/>
 * Usage:
 *  java -Xms700M -Xmx 700M -ea -jar tikaUpdater.jar path_to_tikaUpdater.properties [startRow of result]
 * Parameters:
 *  1. path to config file
 *  2. (optional) start row of result set.
 *      (If the process croaks for some reason, you can start again without going through all
  *      already converted items again).
 *
 * Configuration:
 * TikaUpdater requires two configuration files:
 * 1. copy of cinnamon_config.xml in CINNAMON_HOME_DIR, named: "tikaUpdater.xml"
 *  (this file stores the database configuration)
 * 2. config.properties file, the path to which is specified on the command line.
 *   fields:
 *      server.url=http://cinnamon.test:8080/cinnamon/cinnamon
 *      server.password=admin
 *      server.username=admin
 *      default_repository=cmn_test
 *      objectTypeToConvert=document
 *      encoding=UTF-8
 *      enableDebugging=false
 *      formatName=xml
 *  If you set objectTypeToConvert, you must also set a formatName.
 *  You should set dryRun to false if you want to persist the changes. Otherwise,
 *  tikaUpdater will just go through the motions without making permanent changes,
 *  which is great for test runs.
 *  Setting enableDebugging to true will save the output files in java.io.tmpdir instead of the
 *  given repository, using a filename of "cmn_"+object id + "_" + object name + ".xml".
 *  Changing the encoding setting to cp1252 may be worth a try if you got a legacy platform
 *  with corrupted / broken input files.
 */

public class TikaUpdater {

    Boolean dryRun = true;
    Boolean enableDebugging = false;
    String inputEncoding = "UTF-8";
    String outputEncoding = "UTF-8";

    private Logger log = LoggerFactory.getLogger(this.getClass());
    Conf cinnamonConfig;
    EntityManager em;
    protected Properties config = new Properties();

    String repository;

    Integer firstResult = 0;

    public TikaUpdater() {
    }

    public void setUp(String propertiesFilename) {
        assert new File(propertiesFilename).exists() : "path to config.properties was not found.";
        config = PropertiesLoader.load(new File(propertiesFilename));

        /*
        * this method contains code from HibernateSession for debugging
        * purposes. If in doubt, refactor.
        */
        cinnamonConfig = new Conf("tikaUpdater.xml");
        repository = config.getProperty("default_repository");
        enableDebugging = Boolean.parseBoolean(config.getProperty("enableDebugging", "false"));
        em = HibernateConnector.connect(cinnamonConfig, repository);
        HibernateSession.setLocalEntityManager(em);
    }

    @SuppressWarnings("unchecked")
    public void convert() {
        Format format = null;
        int rowCount = 0;
        if (config.getProperty("formatName") != null) {
            Query formatQuery = em.createQuery("SELECT f FROM Format f WHERE f.name=:name");
            formatQuery.setParameter("name", config.getProperty("formatName"));
            format = (Format) formatQuery.getSingleResult();

        }
        try {
            log.debug("Starting conversion.");
            Query q;
            if (config.getProperty("objectTypeToConvert") != null) {
                String objectTypeName = config.getProperty("objectTypeToConvert");
                log.debug("creating query for objectType: " + objectTypeName);
                q = em.createQuery("SELECT o FROM ObjectSystemData o where o.type.name=:typeName and o.format=:format");
                q.setParameter("typeName", objectTypeName);
                q.setParameter("format", format);
            } else if (format != null){
                log.debug("No object type specified, will look for all objects with format "+format.getName());
                q = em.createQuery("SELECT o FROM ObjectSystemData o WHERE o.format=:format");
                q.setParameter("format", format);
            }
            else{
                log.debug("No object type and/or format defined, will ");
                q = em.createQuery("SELECT o FROM ObjectSystemData o");
            }

            Integer first = firstResult;
            Integer maxResult = 100;
            q.setFirstResult(first);
            q.setMaxResults(maxResult);
            List<ObjectSystemData> objects = q.getResultList();

            while (objects.size() > 0) {
                String workingMsg = String.format("Working on results %d ... %d",
                        first, maxResult + first);
                log.debug(workingMsg);
                System.err.println(workingMsg);
                for (ObjectSystemData osd : objects) {
                    EntityTransaction et = em.getTransaction();
                    et.begin();
                    try {
                        new TikaParser().parse(osd, repository);
                        et.commit();
                        rowCount++;
                    } catch (Exception e) {
                        log.debug("Object: " + osd.getId() + " failed: " + e.getMessage());
                        log.debug("Exception: ", e);
                        e.printStackTrace();
                        if (et.isActive()) {
                            log.debug("rollback transaction.");
                            try {
                                et.rollback();
                            } catch (Exception ex) {
                                log.error("failed to rollback transaction", ex);
                                throw new RuntimeException("A non-recoverable exception prevented rollback of aborted database transaction. Please check the log file.");
                            }

                        }
                        log.debug("After fixing the problem, you should restart this program with rowCount " + rowCount);
                    }
                }

                first += 100;
                q.setFirstResult(first);
                q.setMaxResults(maxResult);
                objects = q.getResultList();
            }
        }
        catch (Exception e){
            e.printStackTrace();
            log.debug("After fixing the problem, you should restart this program with rowCount " + rowCount);
            System.err.println("After fixing the problem, you should restart this program with rowCount " + rowCount);

        } finally {
            em.close();
        }
    }

    public static void main(String[] args) {
        System.out.println("tikaUpdater.");

        TikaUpdater mc = new TikaUpdater();
        if (args.length == 1) {
            mc.setUp(args[0]);
            mc.convert();
        } else if (args.length == 2) {
            mc.setUp(args[0]);
            mc.firstResult = Integer.parseInt(args[1]);
            mc.convert();
        } else {
            System.out
                    .println("Usage: java -Xms800M -Xmx 800M -ea -Dlogback.configurationFile=logback.xml -jar tikaUpdater.jar path_to_config.properties [startRow]");
        }
        System.out.println("Done.");
    }

}
