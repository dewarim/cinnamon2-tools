package net.sourceforge.cinnamon.tool.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.global.Conf;
import server.global.ConfThreadLocal;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * When files from a repository are deleted, the directory structure is not immediately cleaned up.
 * This class is intended for periodic cleanup so your repository is not cluttered with
 * thousands of empty folders.
 */
public class RepositoryCleaner {

    Long deleteCount = 0L;
    Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * @return a collection of the data folders of all configured repositories.
     */
    public Collection<File> getRepositoryFolders(){
        Conf conf = ConfThreadLocal.getConf();
        Collection<String> repositories = conf.getRepositoryList();
        String path = conf.getDataRoot();
        Set<File> folders = new HashSet<File>();
        for(String repository : repositories){
            File repFolder = new File(path, repository);
            if(repFolder.isDirectory()){
                folders.add(repFolder);
            }
        }
        return folders;
    }

    /**
     * Recursively descent into a folder and delete if empty - otherwise descent further
     * if possible. If a folder is successfully deleted, the deleteCount variable is incremented.
     * @param folder the folder which will be deleted if empty or which will be used as a base
     * a for further descent.
     */
    void descent(File folder){
        log.debug("Descent into "+folder.getAbsolutePath());
        for(File file : folder.listFiles()){
            if(file.isDirectory()){
                descent(file);
            }
        }
        if(folder.listFiles().length == 0){
            Boolean deleteSuccess = folder.delete();
            if(deleteSuccess){
                deleteCount++;
            }
            log.debug("deleted "+folder.getAbsolutePath()+": "+deleteSuccess);
        }
    }

    /**
     * Given a collection of root folders, this method will look for any empty sub folders
     * and delete them. The root folders will not be deleted, even if empty.
     * @param repositoryFolders collection of root folders whose empty sub folders will be deleted.
     */
    void removeEmptyFolders(Collection<File> repositoryFolders){
        for(File repFolder : repositoryFolders){
            if(repFolder.isFile()){
                continue;
            }
            // we cannot descent directly into the repositoryFolders because we need
            // to keep them, even if empty.
            for(File subFolder: repFolder.listFiles()){
                if(subFolder.isDirectory()){
                    descent(subFolder);
                }
            }
        }
    }

    public Long getDeleteCount() {
        return deleteCount;
    }

    public void setDeleteCount(Long deleteCount) {
        this.deleteCount = deleteCount;
    }

    public static void main(String[] args){
        RepositoryCleaner cleaner = new RepositoryCleaner();
        Collection<File> repositoryFolders = cleaner.getRepositoryFolders();
        cleaner.removeEmptyFolders(repositoryFolders);
        cleaner.log.debug("Deleted files: "+cleaner.getDeleteCount());
    }
}
