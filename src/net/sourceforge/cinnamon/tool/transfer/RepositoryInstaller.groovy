package net.sourceforge.cinnamon.tool.transfer

/**
 * Install all required files in a Cinnamon Linux VM
 */
class RepositoryInstaller extends RepositoryBaseUtils {

    void doInstall() {
        def target = config.target

        def tempDir = System.getProperty("java.io.tmpdir") + File.separator + "${config.repository}_${config.source.ip}"

        if(config.stopTomcat()){
            stopTomcat(target)
        }

        def repo = config.repository

        def preInstallCommands = [
                "rm -rf /home/${target.cinnamonUser}/cinnamon-data/${repo}/*",
                "rm -rf /home/${target.cinnamonUser}/cinnamon-system/index/${repo}/*",
        ]

        def unpackCmds = [
                "cd /home/${target.cinnamonUser} && tar xvzf ${repo}.index.tar.gz",// && chown -Rv tomcat6:tomcat6 /home/${target.cinnamonUser}/cinnamon-system/index/${repo}",
                "cd /home/${target.cinnamonUser} && tar xvzf ${repo}.files.tar.gz",
                "cd /home/${target.cinnamonUser} && su -c \"dropdb $repo && pg_restore -v -C -d postgres ${repo}.db.dump\" postgres"
        ]

        def postInstallCommands = [
                "rm -f /home/${target.cinnamonUser}/${repo}.index.tar.gz",
                "rm -f /home/${target.cinnamonUser}/${repo}.files.tar.gz",
                "rm -f /home/${target.cinnamonUser}/${repo}.db.dump",
        ]

        def files = [
                "$tempDir/${repo}.index.tar.gz",
                "$tempDir/${repo}.files.tar.gz",
                "$tempDir/${repo}.db.dump",
        ]

        if (config.copyConfigFiles) {
            def configFiles = [
                    "$tempDir/cinnamon_config.xml",
                    "$tempDir/dandelion.properties",
                    "$tempDir/dandelion.log4j.properties",
                    "$tempDir/illicium.log4j.properties",
                    "$tempDir/dandelion-config.groovy",
                    "$tempDir/illicium-config.groovy",
                    "$tempDir/lucene.properties",
            ]
            files.addAll(configFiles)
        }

        if (config.copyWars) {
            def wars = [
                    "$tempDir/cinnamon.war.new",
                    "$tempDir/dandelion.war.new",
                    "$tempDir/illicium.war.new",
            ]
            files.addAll(wars)
            def warCommand = [
                    "/home/${target.cinnamonUser}/replace_cinnamon.sh",
                    "/home/${target.cinnamonUser}/replace_dandelion.sh",
                    "/home/${target.cinnamonUser}/replace_illicium.sh",
            ]
            postInstallCommands.addAll(warCommand)
        }

        preInstallCommands.each {cmd ->
            ant.sshexec(host: target.ip,
                    keyfile: config.cinnamonKey,
                    username: target.username,
                    command: cmd)
        }

        // copy files to /home/$cinnamonUser/.
        def targetDir = "${target.cinnamonUser}@${target.ip}:/home/${target.cinnamonUser}"
        files.each {file ->
            ant.scp(
                    verbose: true,
                    file: file,
                    todir: targetDir,
                    keyfile: config.cinnamonKey,
                    passphrase: '',
                    knownhosts: config.knownHosts,
            )
        }

        // unpack archives
        unpackCmds.each {cmd ->
            ant.sshexec(host: target.ip,
                    keyfile: config.cinnamonKey,
                    username: target.username,
                    command: cmd)
        }

        // run post-install commands
        postInstallCommands.each {cmd ->
            ant.sshexec(host: target.ip,
                    keyfile: config.cinnamonKey,
                    username: target.username,
                    command: cmd)
        }

        if(config.stopTomcat()){
            startTomcat(target)
        }
    }

}
