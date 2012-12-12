package safran.converter;

import org.dom4j.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.dao.DAOFactory;
import server.data.ObjectSystemData;
import server.global.Conf;
import utils.HibernateSession;
import utils.ParamParser;

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
 * An example of how to convert the metadata of all Cinnamon objects in a repository.
 */

public class MetadataConverter {

	private Logger			log			= LoggerFactory.getLogger(this
												.getClass());
	Conf					cinnamon_conf;
	EntityManager			em;
	protected Properties	config		= new Properties();
	
	Integer firstResult = 0;

	public MetadataConverter() {
	}

	public void setUp(String propertiesFilename) {
		try {

			assert new File(propertiesFilename).exists() : "config.properties was not found.";
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(
					propertiesFilename));
			config.load(bis);
			bis.close();

			String[] properties = { "server.url", "server.username",
					"server.password", "default_repository" };
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
			cinnamon_conf = new Conf("safran.xml");
			String repository = config.getProperty("default_repository");

			Map<String, String> myProperties = new HashMap<String, String>();
			String url = cinnamon_conf.getDatabaseConnectionURL(repository);

			log.debug("Using hibernate.connection.url " + url);
			myProperties.put("hibernate.connection.url", url);

			EntityManagerFactory emf = Persistence.createEntityManagerFactory(
					cinnamon_conf.getPersistenceUnit(repository), myProperties);
			em = emf.createEntityManager();
			HibernateSession.setLocalEntityManager(em); // The OSD class MUST
														// have this set.

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public void convert_all_in_one() {

		Query q = em.createQuery("SELECT o FROM ObjectSystemData o");
		List<ObjectSystemData> objects = q.getResultList();
		EntityTransaction et = em.getTransaction();
		et.begin();
		for (ObjectSystemData o : objects) {
			convertMetadata(o);
			em.flush();
		}

		et.commit();
		em.close();
	}

	@SuppressWarnings("unchecked")
	public void convert() {
		System.out.println("Starting conversion.");
		Query q = em.createQuery("SELECT o FROM ObjectSystemData o");
		Integer first = firstResult;
		Integer maxResult = 100;
		q.setFirstResult(first);
		q.setMaxResults(maxResult);
		List<ObjectSystemData> objects = q.getResultList();
		
		while (objects.size() > 0) {

			System.out.println(String.format("Working on results %d ... %d",
					first, maxResult + first));
			EntityTransaction et = em.getTransaction();
			et.begin();
			for (ObjectSystemData o : objects) {
				try{
					convertMetadata(o);
				}
				catch (Exception e) {
					System.out.println("Object: "+o.getId()+" failed: "+e.getMessage());
				}
			}
			et.commit();
//			em.flush();
			first += 100;
			q.setFirstResult(first);
			q.setMaxResults(maxResult);
			objects = q.getResultList();
		}
		em.close();
	}

	/*
	 * This code tries to reverse the following transformation:
	 * Dim result As String = source.Replace("\""", """") result =
	 * result.Replace("\", "\\") result = result.Replace("'", "\'") result =
	 * result.Replace("""", "\""") result = result.Replace("%", "\%") Return
	 * result
	 */

	public void convertMetadata(ObjectSystemData osd) {
		String metadata = osd.getMetadata();
		if (metadata == null) {
			throw new RuntimeException("Metadata is null on #" + osd.getId());
		}

		// setting metadata to empty string will create &lt;meta/&gt;
		if (metadata.trim().length() == 0) {
			log.debug("metadata of " + osd.getId() + "has length 0");
			metadata = "<meta/>";
		} else {
			log
					.debug("length of #" + osd.getId() + " is: "
							+ metadata.length() + " characters");
		}

		try {
			@SuppressWarnings("unused")
			Document doc = ParamParser.parseXmlToDocument(metadata, null);
		} catch (Exception e) {
			log.warn(String.format("OSD %d failed with:\n%s", osd.getId(), e
					.getLocalizedMessage()));
			log.debug(metadata);
			// we got corrupted metadata. Change:

			// \% => %
			metadata = metadata.replace("\\%", "%");

			// \" => "
			metadata = metadata.replace("\\\"", "\"");

			// \' => '
			metadata = metadata.replace("\\'", "'");

			// \\ => \
			metadata = metadata.replace("\\\\", "\\");

			log.debug("converted Metadata:\n" + metadata);
		}

		try {
			osd.setMetadata(metadata);
		} catch (Exception ex) {
			log.error(String.format("metadata of osd #%d is still broken.", osd
					.getId()));
			log.debug(metadata);
		}
	}

	public static void main(String[] args) {
		System.out.println("MetadataConverter.");

		MetadataConverter mc = new MetadataConverter();
		if (args.length == 1) {
			mc.setUp(args[0]);
			mc.convert();
		} 
		else if(args.length == 2){
			mc.setUp(args[0]);
			mc.firstResult = Integer.parseInt(args[1]);
			mc.convert();
		}
		else {
			System.out
					.println("Usage: java -Xms512M -Xmx 512M -ea -jar metadataConverter.jar metadataConverter.properties [startRow]");
		}
		System.out.println("Done.");
	}

}
