package net.sourceforge.cinnamon.tool.converter

import server.data.ObjectSystemData
import org.dom4j.Document
import server.global.Conf
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Write a Document to an OSD
 */
class ContentWriter {

    Logger log = LoggerFactory.getLogger(this.class)
    String encoding = "UTF-8"
    Boolean enableDebugging = false
    Boolean writeBOM = false
    
    ContentWriter(ObjectSystemData osd, Document content, String repositoryName, Conf config){
        def repositoryPath = config.dataRoot
        File output = new File(repositoryPath+File.separator+repositoryName+File.separator+osd.getContentPath())
        log.debug("ContentWriter::Outputpath::${output.absolutePath}")
        writeContent(osd, output, content.asXML())
    }

    ContentWriter(ObjectSystemData osd, Document content, String repositoryName, Conf config,
                  Boolean enableDebugging, String encoding, Boolean writeBOM){
        def repositoryPath
        def outputFilename
        this.encoding = encoding
        this.enableDebugging = enableDebugging
        this.writeBOM = writeBOM

        if(enableDebugging){
            repositoryPath = System.getProperty('java.io.tmpdir')
            outputFilename = repositoryPath+File.separator+"cmn_"+osd.id+"_"+osd.name+".xml"
        }
        else{
            repositoryPath = config.dataRoot
            outputFilename = repositoryPath+File.separator+repositoryName+File.separator+osd.getContentPath()
        }
        File output = new File(outputFilename)
        log.debug("ContentWriter::Outputpath::${output.absolutePath}")
        writeContent(osd, output, content.asXML())
    }

    ContentWriter(ObjectSystemData osd, ByteArrayOutputStream content, String repositoryName, Conf config,
                  Boolean enableDebugging, Boolean writeBOM){
        def repositoryPath
        def outputFilename
        this.enableDebugging = enableDebugging
        this.writeBOM = writeBOM

        if(enableDebugging){
            repositoryPath = System.getProperty('java.io.tmpdir')
            outputFilename = repositoryPath+File.separator+"cmn_"+osd.id+"_"+osd.name+".xml"
        }
        else{
            repositoryPath = config.dataRoot
            outputFilename = repositoryPath+File.separator+repositoryName+File.separator+osd.getContentPath()
        }
        File output = new File(outputFilename)
        log.debug("ContentWriter::Outputpath::${output.absolutePath}")
        writeContent(osd, output, content)
    }

    void writeContent(ObjectSystemData osd, File file, String content){
        // file.write(content, "UTF-8");
        FileOutputStream fos = new FileOutputStream(file)
        int contentLength = content.getBytes(encoding).length
        if(writeBOM){
            System.out.println("Write BOM and content of OSD#"+osd.getId()+" to "+file.absolutePath);
            fos.write(239);
            fos.write(187);
            fos.write(191);
            contentLength += 3
        }
        else{
            System.out.println("Write content without BOM of OSD#"+osd.getId()+" to "+file.absolutePath);
        }
        fos.write(content.getBytes(encoding))
        fos.close()
        osd.setContentSize(contentLength)
    }


    final byte[] xmlHeader = [60,63,120,109,108];
    void writeContent(ObjectSystemData osd, File file, ByteArrayOutputStream content){
        // file.write(content, "UTF-8");
        FileOutputStream fos = new FileOutputStream(file)
        byte[] contentBytes = content.toByteArray()
        int contentLength = contentBytes.length
        if(writeBOM){
            boolean hasXmlHeader = true
            0..4.each{i ->
                if(contentBytes[i] != xmlHeader[i]){
                    hasXmlHeader = false
                }
            }

            if(hasXmlHeader){
                System.out.println("Write UTF-8 BOM and content of OSD#"+osd.getId()+" to "+file.absolutePath)
                fos.write(239);
                fos.write(187);
                fos.write(191);
                contentLength += 3;
            }
            else{
                System.out.println("first bytes do not look like xml header - do not write BOM")
            }
        }
        else{
            System.out.println("Write content without BOM of OSD#"+osd.getId()+" to "+file.absolutePath);
        }
        fos.write(contentBytes)
        fos.close()
        osd.setContentSize(contentLength)
    }

}
