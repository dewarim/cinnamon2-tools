package net.sourceforge.cinnamon.tool.converter

import org.dom4j.Document
import utils.ParamParser
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.dom4j.Node
import org.dom4j.Element
import org.dom4j.ProcessingInstruction

/**
 * Find Processing Instructions in test data.
 */
class PIFinder {

    Logger log = LoggerFactory.getLogger(this.class)

    /**
     * Map of filename to cinnamon id of referenced file.
     */
    Map<String,String> nameIdMap = [:]

    /**
     * List of detached processing instructions found in the given document.
     */
    List<ProcessingInstruction> piNodes = []
    Document doc

    PIFinder(){
    }

    PIFinder(Document document){
        doc = document
        treeWalk(document)
        detachNodes()
    }
    void findPI() {
        String t = new File("testData/test.xml").text
        Document doc = ParamParser.parseXmlToDocument(t)
        treeWalk(doc)
    }

    static void main(String[] args) {
        new PIFinder().findPI()
    }

    public void treeWalk(Document document) {
        treeWalk(document.getRootElement());
    }

    public void treeWalk(Element element) {
        def size=element.nodeCount()
        for (int i = 0; i < size;i++) {
            Node node = element.node(i);
            if(node instanceof ProcessingInstruction){
                ProcessingInstruction pi = (ProcessingInstruction) node
                log.debug("ProcessingInstruction: \ntext: ${pi.text} \ntarget: ${pi.target} \nxml: ${pi.asXML()}")
                if(pi.target.equals('cmn_system_id')){
                    def keyValue = pi.text.split('/')
                    if(keyValue.size() != 2){
                        log.warn("Found strange processing instruction: ${pi.text} - does not seem to be a valid filename/id pair.")
                        continue
                    }
                    nameIdMap.put(keyValue[0],keyValue[1])
                    piNodes.add(pi)
                }
            }

            if (node instanceof Element) {
                treeWalk((Element) node);
            }
        }
    }

    /**
     * Remove ProcessingInstructions and return the changed document.
     */
    void detachNodes(){
        piNodes.each{node ->
            node.detach()
        }
    }
}
