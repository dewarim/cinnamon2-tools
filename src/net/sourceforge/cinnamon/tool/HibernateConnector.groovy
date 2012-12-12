package net.sourceforge.cinnamon.tool

import javax.persistence.EntityManagerFactory
import javax.persistence.Persistence
import server.global.Conf
import javax.persistence.EntityManager

/**
 * Create a Hibernate EntityManager
 */
class HibernateConnector {

    static EntityManager connect(Conf cinnamonConfig, String repository){
        Map<String, String> myProperties = new HashMap<String, String>();
        String url = cinnamonConfig.getDatabaseConnectionURL(repository);
        myProperties.put("hibernate.connection.url", url);

        EntityManagerFactory emf = Persistence.createEntityManagerFactory(
                cinnamonConfig.getPersistenceUnit(repository), myProperties);
        emf.createEntityManager();
    }

}
