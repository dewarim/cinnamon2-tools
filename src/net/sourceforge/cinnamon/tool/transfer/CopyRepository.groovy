package net.sourceforge.cinnamon.tool.transfer

/**
 * Copy a Cinnamon repository from virtual machine A to virtual machine B. <br>
 * Required steps:<br>
 * <ol>
 *     <li>read config file</li>
 *     <li>login to source</li>
 *     <li>stop tomcat</li>
 *     <li>create /tmp/$repo</li>
 *     <li>pg_dump repository.sql</li>
 *     <li>tar cinnamon-data/$repo</li>
 *     <li>tar cinnamon-system/index/$repo</li>
 *     <li>cp cinnamon.war</li>
 *     <li>cp dandelion.war</li>
 *     <li>cp config files</li>
 *     <li>remove other repositories from config files</li>
 *     <li>tar all files into one package</li>
 *     <li>scp package from source to host</li>
 *     <li>scp package from host to target</li>
 *     <li>login to target</li>
 *     <li>unpack package</li>
 *     <li>rm -rf cinnamon-data</li>
 *     <li></li>
 * </ol>
 */
class CopyRepository {

    Config config

    CopyRepository(){}

    void doCopy(){
        new RepositoryPackager(config:config).doPackage()
        new RepositoryInstaller(config:config).doInstall()
    }


    public static void main(String[] args){
        if(args == null || args.length != 1){
            println "CopyRepository usage example: java -jar cinnamonTools.jar config/copyRepository.xml"
        }
        else{
            new CopyRepository(config:new Config(args[0])).doCopy()
        }
    }


}
