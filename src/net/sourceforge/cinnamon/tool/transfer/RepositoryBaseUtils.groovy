package net.sourceforge.cinnamon.tool.transfer

import org.slf4j.LoggerFactory
import org.slf4j.Logger

/**
 *  Basic utility methods for remote repository handling
 */
class RepositoryBaseUtils {

    Logger log = LoggerFactory.getLogger(this.class)
    Config config
    AntBuilder ant = new AntBuilder()

    /**
     * Stop a tomcat6 instance on
     */
    void stopTomcat(host) {
        log.debug("stopping tomcat6 on ${host.name}")
        ant.sshexec(host: host.ip,
                keyfile: config.stopTomcatKey,
                username: host.username,
                command: "service tomcat6 stop")

    }

    void startTomcat(host) {
        log.debug("starting tomcat6 on ${host.name}")
        ant.sshexec(host: host.ip,
                keyfile: config.startTomcatKey,
                username: host.username,
                command: "service tomcat6 restart")
    }

}
