package net.sourceforge.cinnamon.tool.converter;

import net.sourceforge.cinnamon.tool.HibernateConnector;
import net.sourceforge.cinnamon.tool.PropertiesLoader;
import org.dom4j.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Relation;
import server.data.ObjectSystemData;
import server.global.Conf;
import utils.HibernateSession;
import utils.ParamParser;

import javax.persistence.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/*
 * An example of how to extract information from content and transfer it to relation metadata
 * for all Cinnamon objects of a specific type in a repository.
 *
 * The old Cinnamon code stored local paths of related objects as processing instructions
 * in the master XML files. The new code will store this information in the relation's
 * metadata.
 *
 * Usage:
 *  java -Xms512M -Xmx 512M -ea -jar metadataConverter.jar metadataConverter.properties [startRow]
 * Parameters:
 *  1. path to config file
 *  2. (optional) start row of result set.
 *      (If the process croaks for some reason, you can start again without going through all
  *      already converted items again).
 *
 * Configuration:
 * ContentRelationConverter requires two configuration files:
 * 1. copy of cinnamon_config.xml in CINNAMON_HOME_DIR, named: "contentRelationConverter.xml"
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
 *  You should set dryRun to false if you want to persist the changes. Otherwise,
 *  ContentRelationConverter will just go through the motions without making permanent changes,
 *  which is great for test runs.
 *  Setting enableDebugging to true will save the output files in java.io.tmpdir instead of the
 *  given repository, using a filename of "cmn_"+object id + "_" + object name + ".xml".
 *  Changing the encoding setting to cp1252 may be worth a try if you got a legacy platform
 *  with corrupted / broken input files.
 *  writeBOM will result in an ByteOrderMark for UTF8 being written as the first bytes of each OSD's
 *  content. This is needed by broken software. Default (=false) is not to write a BOM.
 */

public class ContentRelationConverter {

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

    public ContentRelationConverter() {
/*         LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
         // print logback's internal status
    StatusPrinter.print(lc);*/
    }

    public void setUp(String propertiesFilename) {
        assert new File(propertiesFilename).exists() : "path to config.properties was not found.";
        config = PropertiesLoader.load(new File(propertiesFilename));

        /*
        * this method contains code from HibernateSession for debugging
        * purposes. If in doubt, refactor.
        */
        cinnamonConfig = new Conf("contentRelationConverter.xml");
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
        String relationType = config.getProperty("relationType");
        if (relationType == null) {
            throw new RuntimeException("No relation type specified. Please add a 'relationType' field to the config.properties.");
        }

        try {
            log.debug("Starting conversion.");
            Query q;
            if (config.getProperty("objectTypeToConvert") != null) {
                String objectTypeName = config.getProperty("objectTypeToConvert");
                log.debug("creating query for objectType: " + objectTypeName);
                q = em.createQuery("SELECT o FROM ObjectSystemData o where o.type.name=:typeName");
                // debugging:
                // q = em.createQuery("SELECT o FROM ObjectSystemData o where o.type.name=:typeName and o.name=:name");
                // q.setParameter("name", "xxx");
                q.setParameter("typeName", objectTypeName);
            } else {
                log.debug("No object type specified, will look for all objects.");
                q = em.createQuery("SELECT o FROM ObjectSystemData o");
            }

            Integer first = firstResult;
            Integer maxResult = 100;
            q.setFirstResult(first);
            q.setMaxResults(maxResult);
            List<ObjectSystemData> objects = q.getResultList();
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
                        PIFinder pif = new PIFinder(doc);
                        doc = pif.getDoc(); // doc without the unnecessary processing instructions

                        for (Map.Entry<String, String> mapEntry : pif.getNameIdMap().entrySet()) {
                            Query relQ = em.createQuery("select r from Relation r where r.leftOSD=:osd and r.rightOSD.id=:id and r.type.name = :rType");
                            relQ.setParameter("rType", relationType);
                            relQ.setParameter("osd", osd);
                            relQ.setParameter("id", Long.parseLong(mapEntry.getValue()));
                            List<Relation> relations = relQ.getResultList();
                            if (relations.isEmpty()) {
                                log.debug("relation between " + osd.getId() + " and " + mapEntry.getValue() + " does not exist - next.");
                                continue;
                            }
                            Relation relation = relations.get(0);
                            log.debug("found relation: " + relation.getId());
                            String meta = "<meta><filename>" + mapEntry.getKey() + "</filename></meta>";
                            if (dryRun) {
                                log.debug("Would now set relation metadata to: " + meta);
                            } else {
                                log.debug("set metadata on relation: " + meta);
                                relation.setMetadata(meta);

                            }
                            docWasChanged = true;
                        }

                        if (dryRun) {
                            log.debug("Would now write OSD content to file if necessary.");
                        } else {
                            if (docWasChanged) {
                                log.debug("write changed content");
                                new ContentWriter(osd, doc, repository, cinnamonConfig,
                                        enableDebugging, outputEncoding, writeBOM);
                            } else {
                                log.debug("OSD #" + osd.getId() + " was not changed; next.");
                            }
                        }

                        et.commit();

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
        System.out.println("ContentRelationConverter.");

        ContentRelationConverter mc = new ContentRelationConverter();
        if (args.length == 1) {
            mc.setUp(args[0]);
            mc.convert();
        } else if (args.length == 2) {
            mc.setUp(args[0]);
            mc.firstResult = Integer.parseInt(args[1]);
            mc.convert();
        } else {
            System.out
                    .println("Usage: java -Xms512M -Xmx 512M -ea -Dlogback.configurationFile=logback.xml -jar contentRelationConverter.jar path_to_config.properties [startRow]");
        }
        System.out.println("Done.");
    }

}
