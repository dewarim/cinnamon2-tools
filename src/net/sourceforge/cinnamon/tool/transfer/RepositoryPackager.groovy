package net.sourceforge.cinnamon.tool.transfer

/**
 * Create a package of a repository and all necessary configuration files.
 */
class RepositoryPackager extends RepositoryBaseUtils {

    void doPackage() {
        def source = config.source

        def tempDir = System.getProperty("java.io.tmpdir") + File.separator + "${config.repository}_${source.ip}"
        // ant.delete(dir:tempDir, verbose:true) // does not delete the dir. strange.
        new File(tempDir).deleteDir()
        ant.mkdir(dir: tempDir)

        if(config.stopTomcat()){
            stopTomcat(source)
        }

        def repo = config.repository

        def commands = [
                "rm -rf /tmp/${repo}.*",
                /*
                 * pg_dump:
                 * -F p = use custom format
                 */
                "pg_dump -F c $repo > /tmp/${repo}.db.dump",
                "tar cvzf /tmp/${repo}.files.tar.gz cinnamon-data/$repo/",
                "tar cvzf /tmp/${repo}.index.tar.gz cinnamon-system/index/$repo"
        ]

        commands.each {cmd ->
            ant.sshexec(host: source.ip,
                    keyfile: config.cinnamonKey,
                    username: source.cinnamonUser,
                    command: cmd)
        }

        def files = [
                "${source.cinnamonUser}@${source.ip}:/tmp/${repo}.index.tar.gz",
                "${source.cinnamonUser}@${source.ip}:/tmp/${repo}.files.tar.gz",
                "${source.cinnamonUser}@${source.ip}:/tmp/${repo}.db.dump",
        ]

        if (config.copyConfigFiles) {
            def configFiles = [
                    "${source.cinnamonUser}@${source.ip}:/home/${source.cinnamonUser}/cinnamon_config.xml",
                    "${source.cinnamonUser}@${source.ip}:/home/${source.cinnamonUser}/dandelion-config.groovy",
                    "${source.cinnamonUser}@${source.ip}:/home/${source.cinnamonUser}/illicium-config.groovy",
                    "${source.cinnamonUser}@${source.ip}:/home/${source.cinnamonUser}/dandelion.properties",
                    "${source.cinnamonUser}@${source.ip}:/home/${source.cinnamonUser}/logback.xml",
                    "${source.cinnamonUser}@${source.ip}:/home/${source.cinnamonUser}/dandelion.log4j.properties",
                    "${source.cinnamonUser}@${source.ip}:/home/${source.cinnamonUser}/illicium.log4j.properties",
                    "${source.cinnamonUser}@${source.ip}:/home/${source.cinnamonUser}/lucene.properties"
            ]
            files.addAll(configFiles)
        }

        if (config.copyWars) {
            def wars = [
                    "${source.cinnamonUser}@${source.ip}:/home/${source.cinnamonUser}/cinnamon.war.new",
                    "${source.cinnamonUser}@${source.ip}:/home/${source.cinnamonUser}/dandelion.war.new",
                    "${source.cinnamonUser}@${source.ip}:/home/${source.cinnamonUser}/illicium.war.new",
            ]
            files.addAll(wars)
        }

        // log.debug("keyfile: ${config.cinnamonKey}\nknownhosts: ${config.knownHosts}")
        files.each {file ->
            ant.scp(
                    verbose: true,
                    file: file,
                    todir: tempDir,
                    keyfile: config.cinnamonKey,
                    passphrase: '',
                    knownhosts: config.knownHosts,
            )
        }

        if(config.stopTomcat()){
            startTomcat(source)
        }
    }

}
