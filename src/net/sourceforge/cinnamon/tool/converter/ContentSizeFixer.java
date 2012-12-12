package net.sourceforge.cinnamon.tool.converter;

import net.sourceforge.cinnamon.tool.PropertiesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.data.ObjectSystemData;
import server.global.Conf;
import utils.HibernateSession;

import javax.persistence.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/*
 * ContentSizeFixer: Check size of objects in file system and compare with database contentSize field - fix if needed.
 *
 * Usage:
 *  java -Xms512M -Xmx 512M -ea -jar contentSizeFixer.jar path-to-config.properties [startRow]
 * Parameters:
 *  1. path to config file
 *  2. (optional) start row of result set.
 *      (If the process croaks for some reason, you can start again without going through all
  *      already converted items again).
 *
 * Configuration:
 * ContentSizeFixer requires two configuration files:
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
 *
 *  You should set dryRun to false if you want to persist the changes. Otherwise,
 *  contentSizeFixer will just go through the motions without making permanent changes,
 *  which is great for test runs.
 */

public class ContentSizeFixer {

    Boolean dryRun = true;

    private Logger log = LoggerFactory.getLogger(this.getClass());
    Conf cinnamon_conf;
    EntityManager em;
    protected Properties config = new Properties();

    String repository;

    Integer firstResult = 0;

    public ContentSizeFixer() {
/*         LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
         // print logback's internal status
    StatusPrinter.print(lc);*/
    }

    public void setUp(String propertiesFilename) {
        assert new File(propertiesFilename).exists() : "path to config.properties was not found.";
        config = PropertiesLoader.load(new File(propertiesFilename));

        String[] properties = {"server.url", "server.username",
                "server.password", "default_repository"};
        for (String p : properties) {
            log.debug(String.format("testing property: %s==%s", p, config
                    .getProperty(p)));
            assert config.getProperty(p) != null : String.format(
                    "Property %s is not set.", p);
        }

        /*
        * this method contains code from HibernateSession for debugging
        * purposes. If in doubt, refactor.
        */
        cinnamon_conf = new Conf("contentRelationConverter.xml");
        repository = config.getProperty("default_repository");
        dryRun = Boolean.parseBoolean(config.getProperty("dryRun", "true"));
        Map<String, String> myProperties = new HashMap<String, String>();
        String url = cinnamon_conf.getDatabaseConnectionURL(repository);

        log.debug("Using hibernate.connection.url " + url);
        myProperties.put("hibernate.connection.url", url);

        EntityManagerFactory emf = Persistence.createEntityManagerFactory(
                cinnamon_conf.getPersistenceUnit(repository), myProperties);
        em = emf.createEntityManager();
        HibernateSession.setLocalEntityManager(em); // The OSD class MUST
        // have this set.

    }

    @SuppressWarnings("unchecked")
    public void fixContentSize() {

        log.debug("FixContentSize.start.");
        Query q = em.createQuery("SELECT o FROM ObjectSystemData o where o.contentSize > 0");

        Integer first = firstResult;
        Integer maxResult = 100;
        q.setFirstResult(first);
        q.setMaxResults(maxResult);
        List<ObjectSystemData> objects = q.getResultList();
        while (objects.size() > 0) {

            log.debug(String.format("Working on results %d ... %d",
                    first, maxResult + first));
            EntityTransaction et = em.getTransaction();
            et.begin();
            for (ObjectSystemData osd : objects) {
                try {
                    Integer length = osd.getContentAsBytes(repository).length;
                    // would be even simpler to get $file.size(), but that would require more lines of code.
                    if (length != osd.getContentSize().intValue()) {
                        log.debug("content size is wrong: correct length is " + length + " instead of " + osd.getContentSize() + " - fixing");
                        if (dryRun) {
                            log.debug("Would now fix OSD content size.");
                        } else {
                            osd.setContentSize(length.longValue());
                        }
                    }
                } catch (Exception e) {
                    log.debug("Object: " + osd.getId() + " failed: " + e.getMessage());
                    log.debug("Exception: ", e);
                }
            }
            et.commit();
            first += 100;
            q.setFirstResult(first);
            q.setMaxResults(maxResult);
            objects = q.getResultList();
        }
        em.close();
    }

    public static void main(String[] args) {
        System.out.println("ContentSizeFixer: Check size of objects in file system and compare with database contentSize field - fix if needed.");

        ContentSizeFixer mc = new ContentSizeFixer();
        if (args.length == 1) {
            mc.setUp(args[0]);
            mc.fixContentSize();
        } else if (args.length == 2) {
            mc.setUp(args[0]);
            mc.firstResult = Integer.parseInt(args[1]);
            mc.fixContentSize();
        } else {
            System.out
                    .println("Usage: java -Xms512M -Xmx 512M -ea -jar contentSizeFixer.jar path-to-config.properties [startRow]");
        }
        System.out.println("Done.");
    }

}
