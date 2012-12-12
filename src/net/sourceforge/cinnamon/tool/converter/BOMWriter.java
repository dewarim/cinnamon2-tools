package net.sourceforge.cinnamon.tool.converter;

import net.sourceforge.cinnamon.tool.HibernateConnector;
import net.sourceforge.cinnamon.tool.PropertiesLoader;
import org.dom4j.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Format;
import server.Relation;
import server.data.ObjectSystemData;
import server.global.Conf;
import utils.HibernateSession;
import utils.ParamParser;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/*
 * Walk through all objects of a repository, and if they are in XML format,
 * add a UTF8-BOM (or remove it, depending on configuration).
 * 
 * Usage:
 *  java -Xms1000M -Xmx1000M -ea -jar bomWriter.jar path_to_bomWriter.properties [startRow of result]
 * Parameters:
 *  1. path to config file
 *  2. (optional) start row of result set.
 *      (If the process croaks for some reason, you can start again without going through all
  *      already converted items again).
 *
 * Configuration:
 * ContentRelationConverter requires two configuration files:
 * 1. copy of cinnamon_config.xml in CINNAMON_HOME_DIR, named: "bomWriter.xml"
 *  (this file stores the database configuration)
 * 2. config.properties file, the path to which is specified on the command line.
 *   fields:
 *      server.url=http://cinnamon.test:8080/cinnamon/cinnamon
 *      server.password=admin
 *      server.username=admin
 *      default_repository=cmn_test
 *      objectTypeToConvert=document
 *      dryRun=true
 *      relationType=child_content
 *      encoding=UTF-8
 *      enableDebugging=false
 *      writeBOM=false
 *      formatName=xml
 *  You should set dryRun to false if you want to persist the changes. Otherwise,
 *  BOMWriter will just go through the motions without making permanent changes,
 *  which is great for test runs.
 *  Setting enableDebugging to true will save the output files in java.io.tmpdir instead of the
 *  given repository, using a filename of "cmn_"+object id + "_" + object name + ".xml".
 *  Changing the encoding setting to cp1252 may be worth a try if you got a legacy platform
 *  with corrupted / broken input files.
 *  writeBOM will result in an ByteOrderMark for UTF8 being written as the first bytes of each OSD's
 *  content. This is needed by broken software. Default (=false) is not to write a BOM.
 */

public class BOMWriter {

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

    public BOMWriter() {
    }

    public void setUp(String propertiesFilename) {
        assert new File(propertiesFilename).exists() : "path to config.properties was not found.";
        config = PropertiesLoader.load(new File(propertiesFilename));

        /*
        * this method contains code from HibernateSession for debugging
        * purposes. If in doubt, refactor.
        */
        cinnamonConfig = new Conf("bomWriter.xml");
        repository = config.getProperty("default_repository");
        dryRun = Boolean.parseBoolean(config.getProperty("dryRun", "true"));
        enableDebugging = Boolean.parseBoolean(config.getProperty("enableDebugging", "false"));
        inputEncoding = config.getProperty("inputEncoding", "UTF-8");
        outputEncoding = config.getProperty("outputEncoding", "UTF-8");
        em = HibernateConnector.connect(cinnamonConfig, repository);
        HibernateSession.setLocalEntityManager(em);
    }

    @SuppressWarnings("unchecked")
    public void convert() {
        Boolean writeBOM = config.getProperty("writeBOM", "false").equals("true");
        Format format = null;
        if (config.getProperty("formatName") == null) {
            throw new RuntimeException("You must specify the parameter formatName in the properties file.");
        }
        Query formatQuery = em.createQuery("SELECT f FROM Format f WHERE f.name=:name");
        formatQuery.setParameter("name", config.getProperty("formatName"));
        format = (Format) formatQuery.getSingleResult();

        try {
            log.debug("Starting conversion.");
            Query q;
            if (config.getProperty("objectTypeToConvert") != null) {
                String objectTypeName = config.getProperty("objectTypeToConvert");
                log.debug("creating query for objectType: " + objectTypeName);
                q = em.createQuery("SELECT o FROM ObjectSystemData o where o.type.name=:typeName and o.format=:format");
                // debugging:
                // q = em.createQuery("SELECT o FROM ObjectSystemData o where o.type.name=:typeName and o.name=:name");
                // q.setParameter("name", "xxx");
                q.setParameter("typeName", objectTypeName);

            } else {
                log.debug("No object type specified, will look for all objects.");
                q = em.createQuery("SELECT o FROM ObjectSystemData o WHERE o.format=:format");
            }
            q.setParameter("format", format);
            Integer first = firstResult;
            Integer maxResult = 100;
            q.setFirstResult(first);
            q.setMaxResults(maxResult);
            List<ObjectSystemData> objects = q.getResultList();
            int rowCount = 0;
            while (objects.size() > 0) {

                log.debug(String.format("Working on results %d ... %d",
                        first, maxResult + first));

                for (ObjectSystemData osd : objects) {
                    Boolean docWasChanged = false;
                    EntityTransaction et = em.getTransaction();
                    et.begin();
                    try {
                        String content = osd.getContent(repository, inputEncoding);
//                    log.debug("Would now convert object: " + o.getId());
                        Document doc = ParamParser.parseXmlToDocument(content);

                        if (dryRun) {
                            log.debug("Would now write OSD content to file if necessary.");
                        } else {
                            log.debug("write changed content");
                            new ContentWriter(osd, doc, repository, cinnamonConfig,
                                    enableDebugging, outputEncoding, writeBOM);
                        }

                        et.commit();
                        rowCount++;
                    } catch (Exception e) {
                        log.debug("Object: " + osd.getId() + " failed: " + e.getMessage());
                        log.debug("Exception: ", e);
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
        } finally {
            em.close();
        }
    }

    public static void main(String[] args) {
        System.out.println("BOMWriter.");

        BOMWriter mc = new BOMWriter();
        if (args.length == 1) {
            mc.setUp(args[0]);
            mc.convert();
        } else if (args.length == 2) {
            mc.setUp(args[0]);
            mc.firstResult = Integer.parseInt(args[1]);
            mc.convert();
        } else {
            System.out
                    .println("Usage: java -Xms1000M -Xmx 1000M -ea -Dlogback.configurationFile=logback.xml -jar bomWriter.jar path_to_config.properties [startRow]");
        }
        System.out.println("Done.");
    }

}
