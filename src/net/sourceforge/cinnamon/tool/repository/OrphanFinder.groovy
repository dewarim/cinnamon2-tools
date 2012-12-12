package net.sourceforge.cinnamon.tool.repository

import utils.HibernateSession
import javax.persistence.Persistence
import javax.persistence.EntityManagerFactory
import server.global.Conf
import net.sourceforge.cinnamon.tool.HibernateConnector
import net.sourceforge.cinnamon.tool.PropertiesLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.NoResultException
import server.data.ObjectSystemData

/**
 * <h1>OrphanFinder</h1>
 * <p>
 *     Find orphaned files and send them to the happy ever-after in /dev/null
 * </p><p>
 * If a server is shutdown unexpectedly in the last stages of removing some content,
 * there exists a chance that content files may be left lying around in the
 * file system. This class is responsible for finding the orphaned files and
 * removing them from the repository storage area.
 * </p>
 * <p>
 * Command line parameter: path to config.properties file.<br>
 * Example: java -jar -Dlogback.configurationFile=logback.xml orphanFinder.jar config.properties
 * </p>
 * <p>
 * Config file fields:</p>
 * <ul>
 *     <li>server.url=http://cinnamon.test:8080/cinnamon/cinnamon</li>
 *     <li>server.password=admin</li>
 *     <li>server.username=admin</li>
 *     <li>default_repository=cmn_test</li>
 *     <li>dryRun=true</li>
 * </ul>
 */
class OrphanFinder {

    Boolean dryRun = true;

    private Logger log = LoggerFactory.getLogger(this.getClass());
    Conf cinnamonConfig;
    EntityManager em;
    protected Properties config = new Properties();
    String repository;

    OrphanFinder(String propertiesFilename) {
        assert new File(propertiesFilename).exists(): "'$propertiesFilename' was not found.";
        config = PropertiesLoader.load(propertiesFilename);

        /*
        * this method contains code from HibernateSession for debugging
        * purposes. If in doubt, refactor.
        */
        cinnamonConfig = new Conf("cinnamon_config.xml");
        repository = config.getProperty("default_repository");
        dryRun = Boolean.parseBoolean(config.getProperty("dryRun", "true"));

        em = HibernateConnector.connect(cinnamonConfig, repository);
        HibernateSession.setLocalEntityManager(em);
    }

    void findOrphans() {
        def repositoryDir = new File(cinnamonConfig.dataRoot + File.separator + repository)
        repositoryDir.eachFileRecurse {file ->
            if (file.isFile()) {
                log.debug("Looking at: ${file.absolutePath}")
                Query query = em.createQuery("SELECT o FROM ObjectSystemData o WHERE o.contentPath LIKE :path ")
                query.setParameter('path', "%${file.name}".toString())
                List<ObjectSystemData> osds = query.getResultList()
                if (osds.isEmpty()) { // no osd by that name
                    if (dryRun) {
                        log.debug("Would now delete ${file.absolutePath}")
                    }
                    else {
                        log.debug("Going to delete orphaned file ${file.absolutePath}")
                        Boolean result = file.delete()
                        assert result: "failed to delete ${file.absolutePath}"
                    }
                }
            }
        }
    }

    static void main(String[] args) {
        if (args.length != 1) {
            println "OrphanFinder\nusage: java -Xms 1G -Xmx 1G  -Dlogback.configurationFile=logback.xml -jar orphanFinder.jar config.properties"
            System.exit(1)
        }
        def morpheus = new OrphanFinder(args[0])
        morpheus.findOrphans()
    }

}
