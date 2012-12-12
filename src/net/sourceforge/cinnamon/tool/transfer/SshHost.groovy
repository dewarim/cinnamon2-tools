package net.sourceforge.cinnamon.tool.transfer

/**
 *
 */
class SshHost {

    String name
    String ip
    String username // root account
    String cinnamonUser

    /**
     * Given a Groovy GPathResult node, construct a SshHost object from it.
     * @param hostNode the GPathResult node.
     */
    SshHost(hostNode) {
        name = hostNode.name.text()
        ip = hostNode.ip.text()
        username = hostNode.username.text()
        cinnamonUser = hostNode.cinnamonUser.text()
    }
}
