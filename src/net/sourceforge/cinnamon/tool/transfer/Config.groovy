package net.sourceforge.cinnamon.tool.transfer

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Configuration for CopyRepository.
 */
class Config {

    Logger log = LoggerFactory.getLogger(this.class)
    SshHost source
    SshHost target
    String configPath
    String repository

    def conf

    Config(String name){
        configPath = System.getProperty("COPY_REPOSITORY_CONFIG_DIR", "config")
        log.debug("configPath: "+configPath)
        File configFile = new File(name)
        conf = new XmlSlurper().parse(configFile)
        repository = conf.repository.text()
    }

    SshHost getSource(){
        if(! source){
            source = new SshHost(conf.source)
        }
        return source
    }

    SshHost getTarget(){
        if(! target){
            target = new SshHost(conf.target)
        }
        return target
    }

    String getStartTomcatKey(){
        return configPath + File.separator + conf?.startTomcatKey?.text()
    }

    String getStopTomcatKey(){
        return configPath + File.separator + conf?.stopTomcatKey?.text()
    }

    String getCinnamonKey(){
        return configPath + File.separator + conf?.cinnamonKey?.text()
    }

    String getKnownHosts(){
         return configPath + File.separator + 'known_hosts.dat'
    }

    String getRepository(){
        return repository
    }

    Boolean getCopyConfigFiles(){
        return conf.copyConfigFiles?.text()?.equals('true')
    }

    Boolean getCopyWars(){
        return conf.copyWars?.text()?.equals('true')
    }

    Boolean stopTomcat(){
        return conf.stopTomcat?.text()?.equals('true')
    }
}
