package net.sourceforge.cinnamon.tool.setup

import server.global.ConfThreadLocal
import server.exceptions.CinnamonConfigurationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import utils.HibernateSession

import javax.persistence.EntityManager
import net.sourceforge.cinnamon.tool.PropertiesLoader
import net.sourceforge.cinnamon.tool.HibernateConnector
import javax.persistence.Query
import server.User
import javax.persistence.EntityTransaction
import server.ObjectType
import server.FolderType
import server.index.IndexType
import server.Group
import server.ConfigEntry
import server.Format
import server.CustomTable
import server.Acl

import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import server.Permission
import server.RelationResolver
import server.transformation.Transformer
import server.trigger.ChangeTriggerType
import server.i18n.Language
import server.i18n.UiLanguage
import server.i18n.Message

import org.hibernate.criterion.Example
import server.trigger.ChangeTrigger
import org.hibernate.Session
import server.RelationType
import server.index.IndexGroup
import server.index.IndexItem
import server.dao.FolderDAO
import server.dao.DAOFactory
import server.Folder
import server.exceptions.CinnamonException
import server.GroupUser
import javax.persistence.NoResultException
import server.AclEntry
import server.AclEntryPermission
import server.lifecycle.LifeCycle
import server.lifecycle.LifeCycleState
import server.lifecycle.IState

/**
 * Load items for a Cinnamon repository from an XML configuration file.
 */
class ItemLoader {

    Logger log = LoggerFactory.getLogger(this.class)

    def items
    Boolean dryRun = true
    Properties config
    ConfThreadLocal cinnamonConfig = ConfThreadLocal.getConf()
    EntityManager em
    String repository

    ItemLoader(String repository, File setupDir) {

        File dataDir = setupDir ?: new File(config.getSystemRoot() + File.separator + "setup" + File.separator + repository);
        if (!dataDir.exists()) {
            throw new CinnamonConfigurationException("Setup dir for repository '$repository' does not exist in ${dataDir?.absolutePath}.")
        }

        // load and check properties
        config = PropertiesLoader.load(dataDir)

        this.repository = repository ?: config.getProperty("default_repository");
        dryRun = Boolean.parseBoolean(config.getProperty("dryRun", "true"));

        em = HibernateConnector.connect(cinnamonConfig, repository)
        HibernateSession.setLocalEntityManager(em);

        File itemFile = new File(dataDir, "items.xml");
        if (!itemFile.exists()) {
            throw new CinnamonConfigurationException("items.xml file for repository '$repository' does not exist.")
        }
        try {
            items = new XmlSlurper().parse(itemFile)
        }
        catch (RuntimeException e) {
            log.debug("Failed to parse items.xml:", e)
            throw new RuntimeException('Could not parse items.xml. Please check that it\'s well-formed and valid XML')
        }
    }

    void setup() {
        EntityTransaction et = em.getTransaction()
        try {
            et.begin()
            setupUsers(items.users.user)
            setupObjectTypes(items.objectTypes.objectType)
            setupFolderTypes(items.folderTypes.folderType)
            setupIndexTypes(items.indexTypes.indexType)
            setupGroups(items.groups.group)
            setupConfigEntries(items.configEntries.configEntry)
            setupFormats(items.formats.format)
            setupAcls(items.acls.acl)
            setupPermissions(items.permissions.permission)
            setupRelationResolvers(items.relationResolvers.relationResolver)
            setupChangeTriggerTypes(items.changeTriggerTypes.changeTriggerType)
            setupLanguages(items.languages.language)
            setupUiLanguages(items.uiLanguages.uiLanguage)
            setupIndexGroups(items.indexGroups.indexGroup)

            setupTransformers(items.transformers.transformer)
            setupCustomTables(items.customTables.customTable)
            setupMessages(items.messages.message)
            setupChangeTriggers(items.changeTriggers.changeTrigger)
            setupRelationTypes(items.relationTypes.relationType)
            setupIndexItems(items.indexItems.indexItem)
            setupFolders(items.folders.folder)
            setupGroupUsers(items.groupUsers.groupUser)
            setupAclEntries(items.aclEntries.aclEntry)
            setupAclEntryPermissions(items.aclEntryPermissions.aclEntryPermission)
            setupLifecycles(items.lifeCycles.lifeCycle, items.lifeCycleStates.lifeCycleState)
            setupLifeCycleStates(items.lifeCycles.lifeCycle, items.lifeCycleStates.lifeCycleState)

            /*
            * TODO: complex types
            * ObjectSystemData(?)
            * Relation
            */
            if (dryRun) {
                if (et.isActive()) {
                    log.info("Rollback issued because we are only doing a dry-run.")
                    et.rollback()
                }
            }
            else {
                log.info("Commit database transaction.")
                et.commit()
            }
        }
        catch (Exception e) {
            log.error("An error ocurred during setup - will try to issue rollback command to database.", e)
            if (et.isActive()) {
                et.rollback()
            }
        }
    }

    void setupIndexTypes(indexTypes) {
        def fields = ['name', 'indexer_class', 'va_provider_class', 'data_type']
        indexTypes.each {indexType ->
            def name = indexType.name.text()
            log.debug("indexType: $name")
            IndexType it = (IndexType) fetchItemByClassAndName(IndexType.class, name)
            if (it) {
                log.debug("IndexType $name already exists.")
            }
            else {
                it = new IndexType(createParameterMap(indexType, fields))
                em.persist(it)
            }
        }
    }

    void setupIndexGroups(indexGroups) {
        def fields = ['name']
        indexGroups.each {indexGroup ->
            def name = indexGroup.name.text()
            log.debug("indexGroup: $name")
            IndexGroup group = (IndexGroup) fetchItemByClassAndName(IndexGroup.class, name)
            if (group) {
                log.debug("IndexGroup $name already exists.")
            }
            else {
                group = new IndexGroup(createParameterMap(indexGroup, fields))
                em.persist(group)
            }
        }
    }

    void setupLanguages(languages) {
        def fields = ['iso_code', 'metadata']
        languages.each {lang ->
            def isoCode = lang.iso_code.text()
            log.debug("language: $isoCode")
            Language language = (Language) fetchItemByClassAndField(Language.class, 'isoCode', isoCode)
            if (language) {
                log.debug("Language $isoCode already exists.")
            }
            else {
                language = new Language(createParameterMap(lang, fields))
                em.persist(language)
            }
        }
    }

    void setupUiLanguages(languages) {
        languages.each {lang ->
            String isoCode = lang.iso_code.text()
            log.debug("language: $isoCode")
            UiLanguage language = (UiLanguage) fetchItemByClassAndField(UiLanguage.class, 'isoCode', isoCode)
            if (language) {
                log.debug("Language $isoCode already exists.")
            }
            else {
                language = new UiLanguage(isoCode)
                em.persist(language)
            }
        }
    }

    void setupChangeTriggerTypes(triggerTypes) {
        def fields = ['name', 'description', 'trigger_class']
        triggerTypes.each {trigType ->
            def name = trigType.name.text()
            log.debug("changeTriggerType: $name")
            ChangeTriggerType ctt = (ChangeTriggerType) fetchItemByClassAndName(ChangeTriggerType.class, name)
            if (ctt) {
                log.debug("ChangeTriggerType $name already exists.")
            }
            else {
                ctt = new ChangeTriggerType(createParameterMap(trigType, fields))
                em.persist(ctt)
            }
        }
    }

    void setupTransformers(transformers) {
        def fields = ['name', 'description', 'transformer_class', 'source_format_id', 'target_format_id']
        transformers.each {former ->
            def name = former.name.text()
            log.debug("transformer: $name")
            Transformer transformer = (Transformer) fetchItemByClassAndName(Transformer.class, name)
            if (transformer) {
                log.debug("Transformer $name already exists.")
            }
            else {
                Format sourceFormat = (Format) fetchItemByClassAndName(Format.class, former.source_format.text())
                Format targetFormat = (Format) fetchItemByClassAndName(Format.class, former.target_format.text())
                former.appendNode {
                    source_format_id {
                        mkp.yield String.valueOf(sourceFormat.id)
                    }
                    target_format_id {
                        mkp.yield String.valueOf(targetFormat.id)
                    }
                }

                String serialFormer = serializeXml(former)
//                log.debug("transformer reparsed: $serialFormer")
                def reParsed = new XmlSlurper().parseText(serialFormer)
                transformer = new Transformer(createParameterMap(reParsed, fields))
                em.persist(transformer)
            }
        }
    }

    void setupLifecycles(lifecycles, lifecycleStates) {
        lifecycles.each {cycle ->
            def name = cycle.name.text()
            def defaultState = cycle.defaultState.text()?.trim()
            log.debug("lifeCycle: $name")
            LifeCycle lifecCycle = findOrCreateLifeCycle(name, cycle, lifecycles, lifecycleStates)
            log.debug("done.")
        }
    }

    void setupLifeCycleStates(lifecycles, lifecycleStates){
        lifecycleStates.each{state ->
            def name = state.name.text()
            def lifeCycle = state.lifeCycle.text()
            log.debug("lifeCycleState: $name")
            LifeCycleState lcs = findOrCreateLifeCycleState(name, lifeCycle, lifecycles, lifecycleStates)
            log.debug("done.")
        }
    }

    LifeCycle findOrCreateLifeCycle(name, cycle, lifecycles, lifecycleStates) {
        LifeCycle lifeCycle = (LifeCycle) fetchItemByClassAndName(LifeCycle.class, name)
        if (lifeCycle) {
            log.debug("LifeCycle $name already exists.")
            return lifeCycle
        }
        lifeCycle = new LifeCycle(name, null)
        em.persist(lifeCycle)
        def defaultState = cycle.defaultState.text()
        if (defaultState) {
            log.debug("default state: $defaultState")
            lifeCycle.defaultState = findOrCreateLifeCycleState(defaultState, cycle, lifecycles, lifecycleStates)
        }
        return lifeCycle
    }

    LifeCycleState findOrCreateLifeCycleState(name, cycle, lifecycles, states) {
        def state = states.find{it.name.text() == name}
        LifeCycleState lcs = (LifeCycleState) fetchItemByClassAndName(LifeCycleState.class, name)
        if(lcs){
            return lcs
        }
        else{
            def stateClass = (Class<? extends IState>) Class.forName(state.stateClass.text())

            lcs = new LifeCycleState()
            lcs.setStateClass(stateClass)
            lcs.setName(name)
            lcs.setParameter(state.parameter.text())
            em.persist(lcs)
            if(state.lifeCycle?.text()?.length() > 0){
                lcs.setLifeCycle(findOrCreateLifeCycle(state.lifeCycle.text(), cycle, lifecycles, states))
            }
            if(state.stateForCopy?.text()?.length() > 0){
                lcs.setLifeCycleStateForCopy(findOrCreateLifeCycleState(state.stateForCopy.text(), cycle, lifecycles, states))
            }
            return lcs
        }
    }

    void setupGroupUsers(groupUsers) {
        groupUsers.each {guser ->
            def groupName = guser.groupName.text()
            def username = guser.username.text()
            Query query = em.createQuery("SELECT gu FROM GroupUser gu WHERE gu.user.name=:username and gu.group.name=:groupName")
            query.setParameter("username", username)
            query.setParameter("groupName", groupName)
            GroupUser groupUser = null
            try {
                groupUser = (GroupUser) query.singleResult
                // no NRE? the GroupUser exists:
                log.debug("GroupUser $groupName::$username already exists")
                return
            }
            catch (NoResultException e) {
                log.debug("will create new GroupUser $groupName::$username")
            }

            User user = (User) fetchItemByClassAndName(User.class, username)
            if (!user) {
                log.warn("Missing user $username")
                throw new RuntimeException('error.missing.user')
            }
            Group group = (Group) fetchItemByClassAndName(Group.class, groupName)
            if (!group) {
                log.warn("Missing group: $groupName")
                throw new RuntimeException('error.missing.group')
            }
            groupUser = new GroupUser(user, group)
            em.persist(groupUser)
        }
    }

    void setupAclEntries(aclEntries) {
        aclEntries.each {entry ->
            def groupName = entry.group.text()
            def aclName = entry.acl.text()
            Query query = em.createQuery("SELECT ae FROM AclEntry ae WHERE ae.acl.name=:acl and ae.group.name=:group")
            query.setParameter("acl", aclName)
            query.setParameter("group", groupName)
            AclEntry ae = null
            try {
                ae = (AclEntry) query.singleResult
                // no NRE? the AE exists:
                log.debug("AclEntry $aclName::$groupName already exists")
                return
            }
            catch (NoResultException e) {
                log.debug("will create new AclEntry $aclName::$groupName")
            }

            Acl acl = (Acl) fetchItemByClassAndName(Acl.class, aclName)
            if (!acl) {
                log.warn("Missing acl $aclName")
                throw new RuntimeException('error.missing.acl')
            }
            Group group = (Group) fetchItemByClassAndName(Group.class, groupName)
            if (!group) {
                log.warn("Missing group: $groupName")
                throw new RuntimeException('error.missing.group')
            }
            ae = new AclEntry(acl, group)
            em.persist(ae)
        }
    }

    void setupAclEntryPermissions(aclEntryPermissions) {
        aclEntryPermissions.each {entry ->
            def groupName = entry.group.text()
            def aclName = entry.acl.text()
            def permissionName = entry.permission.text()
            Query query = em.createQuery("SELECT aep FROM AclEntryPermission aep WHERE aep.aclentry.acl.name=:acl and aep.aclentry.group.name=:group and aep.permission.name=:permission")
            query.setParameter("acl", aclName)
            query.setParameter("group", groupName)
            query.setParameter("permission", permissionName)
            AclEntryPermission aep = null
            try {
                aep = (AclEntryPermission) query.singleResult
                // no NRE? the AEP exists:
                log.debug("AclEntryPermission $aclName::$groupName::$permissionName already exists")
                return
            }
            catch (NoResultException e) {
                log.debug("will create new AclEntryPermission $aclName::$groupName::$permissionName")
            }

            AclEntry ae = null
            try {
                Query aeQuery = em.createQuery("SELECT ae FROM AclEntry ae WHERE ae.acl.name=:acl and ae.group.name=:group")
                aeQuery.setParameter("acl", aclName)
                aeQuery.setParameter("group", groupName)

                ae = (AclEntry) aeQuery.singleResult
            }
            catch (NoResultException e) {
                log.warn("Missing AclEntry: $aclName::$groupName")
            }
            Permission permission = (Permission) fetchItemByClassAndName(Permission.class, permissionName)
            if (!permission) {
                log.warn("Missing permission: $permissionName")
                throw new RuntimeException('error.missing.permission')
            }

            aep = new AclEntryPermission(ae, permission)
            em.persist(aep)
        }
    }

//    void setupRelations(relations) {
    //        // note: name is the name of the relation type!
    //        def fields = ['name', 'leftid', 'rightid', 'metadata']
    //        relations.each {relation->
    //            def typeName = relation.name.text()
    //            def leftOsd
    //            log.debug("transformer: ")
    //            Transformer transformer = (Transformer) fetchItemByClassAndName(Transformer.class, name)
    //            if (transformer) {
    //                log.debug("Transformer $name already exists.")
    //            }
    //            else {
    //                Format sourceFormat = (Format) fetchItemByClassAndName(Format.class, former.source_format.text())
    //                Format targetFormat = (Format) fetchItemByClassAndName(Format.class, former.target_format.text())
    //                former.appendNode {
    //                    source_format_id {
    //                        mkp.yield String.valueOf(sourceFormat.id)
    //                    }
    //                    target_format_id {
    //                        mkp.yield String.valueOf(targetFormat.id)
    //                    }
    //                }
    //
    //                String serialFormer = serializeXml(former)
    //                def reParsed = new XmlSlurper().parseText(serialFormer)
    //                transformer = new Transformer(createParameterMap(reParsed, fields))
    //                em.persist(transformer)
    //            }
    //        }
    //    }

    void setupFolders(folders) {
        def fields = ['name', 'metadata', 'ownerid', 'parentid',
                'typeid', 'aclid'
        ]
        folders.each {folderXml ->
            def path = folderXml.path.text()
            log.debug("folder-path: '$path'")
            DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE)
            FolderDAO fDao = daoFactory.getFolderDAO(em)
            Folder folder = null
            try {
                folder = fDao.findByPath(path);
            }
            catch (CinnamonException e) {
                if (e.message.equals('error.path.invalid')) {
                    log.debug("folder does not exist yet.")
                }
                else {
                    throw new RuntimeException(e)
                }
            }

            if (folder) {
                log.debug("folder already exist as '$path'")
                return
            }

            def parentPath = folderXml.parent_path.text()
            Folder parent = fDao.findByPath(parentPath)
            FolderType folderType = (FolderType) fetchItemByClassAndName(FolderType.class, folderXml.type.text())
            Acl acl = (Acl) fetchItemByClassAndName(Acl.class, folderXml.acl.text())
            User user = (User) fetchItemByClassAndName(User.class, folderXml.owner.text())
            folderXml.appendNode {
                parentid {
                    mkp.yield String.valueOf(parent.id)
                }
                typeid {
                    mkp.yield String.valueOf(folderType.id)
                }
                aclid {
                    mkp.yield String.valueOf(acl.id)
                }
                ownerid {
                    mkp.yield String.valueOf(user.id)
                }
            }

            String serialFolder = serializeXml(folderXml)
            def reParsed = new XmlSlurper().parseText(serialFolder)
            folder = new Folder(createParameterMap(reParsed, fields))
            em.persist(folder)
        }
    }


    void setupIndexItems(indexItems) {
        def fields = ['name', 'search_string', 'search_condition', 'fieldname', 'va_provider_params',
                'index_group_id', 'index_type_id', 'multiple_results', 'systemic',
                'for_content', 'for_metadata', 'for_sys_meta']
        indexItems.each {indexItem ->
            def name = indexItem.name.text()
            log.debug("indexItem: $name")
            IndexItem iItem = (IndexItem) fetchItemByClassAndName(IndexItem.class, name)
            if (iItem) {
                log.debug("IndexItem $name already exists.")
            }
            else {
                IndexGroup indexGroup = (IndexGroup) fetchItemByClassAndName(IndexGroup.class, indexItem.index_group.text())
                IndexType indexType = (IndexType) fetchItemByClassAndName(IndexType.class, indexItem.index_type.text())
                indexItem.appendNode {
                    index_group_id {
                        mkp.yield String.valueOf(indexGroup.id)
                    }
                    index_type_id {
                        mkp.yield String.valueOf(indexType.id)
                    }
                }

                String serialItem = serializeXml(indexItem)
//                log.debug("transformer reparsed: $serialFormer")
                def reParsed = new XmlSlurper().parseText(serialItem)
                iItem = new IndexItem(createParameterMap(reParsed, fields))
                em.persist(iItem)
            }
        }
    }

    void setupRelationTypes(relationTypes) {
        def fields = ['name', 'description', 'transformer_class',
                'leftobjectprotected', 'rightobjectprotected',
                'left_resolver', 'right_resolver'

        ]
        relationTypes.each {type ->
            def name = type.name.text()
            log.debug("relationType: $name")
            RelationType relationType = (RelationType) fetchItemByClassAndName(RelationType.class, name)
            if (relationType) {
                log.debug("RelationType $name already exists.")
            }
            else {
                relationType = new RelationType(createParameterMap(type, fields, false))
                em.persist(relationType)
            }
        }
    }

    void setupRelationResolvers(resolvers) {
        def fields = ['name', 'class_name', 'config']
        resolvers.each {resolverX ->
            def name = resolverX.name.text()
            log.debug("relationResolver: $name")
            RelationResolver resolver = (RelationResolver) fetchItemByClassAndName(RelationResolver.class, name)
            if (resolver) {
                log.debug("RelationResolver $name already exists.")
            }
            else {
                resolver = new RelationResolver(createParameterMap(resolverX, fields))
                em.persist(resolver)
            }
        }
    }

    void setupAcls(acls) {
        def fields = ['name', 'description']
        acls.each {aclXml ->
            def name = aclXml.name.text()
            log.debug("acl: $name")
            Acl acl = (Acl) fetchItemByClassAndName(Acl.class, name)
            if (acl) {
                log.debug("Acl $name already exists.")
            }
            else {
                acl = new Acl(createParameterMap(aclXml, fields))
                em.persist(acl)
            }
        }
    }

    void setupPermissions(permissions) {
        def fields = ['name', 'description']
        permissions.each {perm ->
            def name = perm.name.text()
            log.debug("permission: $name")
            Permission permission = (Permission) fetchItemByClassAndName(Permission.class, name)
            if (permission) {
                log.debug("Permission $name already exists.")
            }
            else {
                permission = new Permission(createParameterMap(perm, fields))
                em.persist(permission)
            }
        }
    }

    String serializeXml(xml) {
        XmlUtil.serialize(new StreamingMarkupBuilder().bind {
            mkp.yield xml
        })
    }

    void setupCustomTables(tables) {
        def fields = ['name', 'connstring', 'jdbc_driver', 'acl_id']
        tables.each {table ->
            def name = table.name.text()
            log.debug("customTable: $name")
            CustomTable customTable = (CustomTable) fetchItemByClassAndName(CustomTable.class, name)
            if (customTable) {
                log.debug("CustomTable $name already exists.")
            }
            else {
                Acl acl = (Acl) fetchItemByClassAndName(Acl.class, table.acl.text())
                // ACLs should already be setup!
                //                if(! acl){
                //                    def aclNode = items.acls?.acl?.find{it.name.text() == table.acl.text()}
                //                    if(! aclNode){
                //                        // create aclNode(s)
                //                    }
                //                }
                //                def node = new groovy.util.slurpersupport.Node(table.name.parent(), 'acl', String.valueOf(acl.id))

                table.appendNode {
                    acl_id {
                        mkp.yield String.valueOf(acl.id)
                    }
                }
                def x = serializeXml(table)
//                log.debug("$x")
                // http://jira.codehaus.org/browse/GROOVY-4021 looks like we have
                // to parse the text again, otherwise createParameterMap will access the original version
                // of the table XML.
                customTable = new CustomTable(createParameterMap(new XmlSlurper().parseText(x), fields))
                em.persist(customTable)
            }
        }
    }

    void setupMessages(messages) {
        def fields = ['message', 'translation', 'ui_language_id']
        messages.each {msg ->
            String mess = msg.message.text()
            String lang = msg.uiLanguage.text()

            log.debug("message: $mess uiLanguage: $lang")
            Query query = em.createQuery("SELECT m FROM Message m WHERE m.message=:message and m.language.isoCode=:isoCode")
            query.setParameter('message', mess)
            query.setParameter('isoCode', lang)

            def mList = query.resultList
            Message message = (Message) (mList.isEmpty() ? null : mList.get(0))

            if (message) {
                log.debug("Message $mess / $lang already exists.")
            }
            else {
                UiLanguage uiLang = (UiLanguage) fetchItemByClassAndField(UiLanguage.class, 'isoCode', lang)
                if (!uiLang) {
                    throw new RuntimeException("error.missing.uiLanguage")
                }

                msg.appendNode {
                    ui_language_id {
                        mkp.yield String.valueOf(uiLang.id)
                    }
                }
                def x = serializeXml(msg)
//                log.debug("$x")
                message = new Message(createParameterMap(new XmlSlurper().parseText(x), fields))
                em.persist(message)
            }
        }
    }

    void setupChangeTriggers(triggers) {
        def fields = ['trigger_type_id', 'ranking', 'command', 'active', 'pre_trigger', 'post_trigger', 'config']
        triggers.each {trig ->
            def cttName = trig.triggerType.text()
            ChangeTriggerType ctt = (ChangeTriggerType) fetchItemByClassAndName(ChangeTriggerType.class, cttName)
            if (!ctt) {
                throw new RuntimeException("error.missing.changeTriggerType")
            }
            trig.appendNode {
                trigger_type_id {
                    mkp.yield String.valueOf(ctt.id)
                }
            }
            def x = serializeXml(trig)
            trig = new XmlSlurper().parseText(x)

            ChangeTrigger c = new ChangeTrigger(createParameterMap(trig, fields))
            Session session = (Session) em.getDelegate()
            List results = session.createCriteria(ChangeTrigger.class).add(Example.create(c)).list()
            if (results.isEmpty()) {
                log.debug("create new ChangeTrigger")
                em.persist(c)
            }
            else {
                log.debug("Found already existing ChangeTrigger")
            }

        }
    }

    void setupFormats(formats) {
        def fields = ['name', 'description', 'contenttype', 'extension']
        formats.each {f ->
            def name = f.name.text()
            log.debug("format: $name")
            Format format = (Format) fetchItemByClassAndName(Format.class, name)
            if (format) {
                log.debug("Format $name already exists.")
            }
            else {
                format = new Format(createParameterMap(f, fields))
                em.persist(format)
            }
        }
    }

    void setupConfigEntries(entries) {
        def fields = ['name', 'config']
        entries.each {entry ->
            def name = entry.name.text()
            log.debug("configEntry: $name")
            ConfigEntry configEntry = (ConfigEntry) fetchItemByClassAndName(ConfigEntry.class, name)
            if (configEntry) {
                log.debug("ConfigEntry $name already exists.")
            }
            else {
                configEntry = new ConfigEntry(createParameterMap(entry, fields))
                log.debug("configEntry config: ${configEntry.config}")
                em.persist(configEntry)
            }
        }
    }

    void setupObjectTypes(objectTypes) {
        def fields = ['name', 'description']
        objectTypes.each {objType ->
            def name = objType.name.text()
            log.debug("objName: $name")
            ObjectType ot = (ObjectType) fetchItemByClassAndName(ObjectType.class, name)
            if (ot) {
                log.debug("ObjectType ${name} already exists.")
            }
            else {
                ot = new ObjectType(createParameterMap(objType, fields))
                em.persist(ot)
            }
        }
    }

    void setupGroups(groups) {
        def fields = ['name', 'description', 'is_user']
        groups.each {groupX ->
            def name = groupX.name.text()
            log.debug("group: $name")
            Group group = (Group) fetchItemByClassAndName(Group.class, name)
            if (group) {
                log.debug("ObjectType ${name} already exists.")
            }
            else {
                group = new Group(createParameterMap(groupX, fields))
                em.persist(group)
            }
        }
    }

    void setupFolderTypes(folderTypes) {
        def fields = ['name', 'description']
        folderTypes.each {folderType ->
            def name = folderType.name.text()
            log.debug("folderName: $name")
            FolderType ft = (FolderType) fetchItemByClassAndName(FolderType.class, name)
            if (ft) {
                log.debug("FolderType ${name} already exists.")
            }
            else {
                ft = new FolderType(createParameterMap(folderType, fields))
                em.persist(ft)
            }
        }
    }

    void setupUsers(users) {
        log.debug("users: ${users}")

        users.each {u ->
            def name = u.name.text()
            log.debug("Searching for user: $name")

            User user = (User) fetchItemByClassAndName(User.class, name)
            if (user) {
                log.debug("Found user ${user.name}. Will skip this user.")
            }
            else {
                log.debug("User ${name} was not found in the database.")
                createUser(u)
            }
        }

    }

/**
 * Load an object of the given class by its name.
 * @param clazz class of the object
 * @param id string representation of the id, which is parsed as a Long value.
 * @return the requested object or, if the object could not be found, null. Will throw a RuntimeException
 * in case it finds more than one object or the name is empty or null.
 */
    Object fetchItemByClassAndName(Class clazz, String name) {
        if (name == null || !name || name.trim().length() == 0) {
            log.debug("id == null")
            throw new RuntimeException("error.missing.name")
        }
        Query q = em.createQuery("select i from ${clazz.name} i where name=:name")
        q.setParameter('name', name)
        def objects = q.resultList
        if (objects.isEmpty()) {
            return null
        }
        else if (objects.size() > 1) {
            throw new RuntimeException("error.name.not.unique")
        }
        return objects.get(0)
    }

/**
 * Load an object of the given class by using a unique field.
 * @param clazz class of the object
 * @param fieldName
 * @param id string representation of the id, which is parsed as a Long value.
 * @return the requested object or, if the object could not be found, null. Will throw a RuntimeException
 * in case it finds more than one object or the name is empty or null.
 */
    Object fetchItemByClassAndField(Class clazz, String fieldName, String fieldValue) {
        if (fieldName == null || fieldValue == null || fieldName.trim().length() == 0 || fieldValue.trim().length() == 0) {
            log.debug("field: '$fieldName' \t value: '$fieldValue'")
            throw new RuntimeException("error.missing.params")
        }
        Query q = em.createQuery("select i from ${clazz.name} i where ${fieldName}=:fieldValue")
        q.setParameter('fieldValue', fieldValue)
        def objects = q.resultList
        if (objects.isEmpty()) {
            return null
        }
        else if (objects.size() > 1) {
            throw new RuntimeException("error.field.not.unique")
        }
        return objects.get(0)
    }

    void createUser(userXml) {
        def fields = ['name', 'pwd', 'fullname', 'description', 'sudoer', 'sudoable', 'email']
        def map = createParameterMap(userXml, fields)
        User user = new User(map)
        em.persist(user);
        log.debug("Created user ${user.name} with id ${user.id}")
    }

    Map<String, String> createParameterMap(xml, fields) {
        return createParameterMap(xml, fields, true)
    }

    Map<String, String> createParameterMap(xml, fields, croakOnNull) {
        Map<String, String> map = new HashMap<String, String>()
        fields.each { field ->
            String val = xml?."$field"?.text()?.trim()
            if (val == null) {
                if (croakOnNull) {
                    throw new RuntimeException("Field $field is not set.")
                }
            }
            map.put(field, val)
        }
        return map
    }

    static void main(String[] args) {
        def itemLoader = new ItemLoader('cmn_test', new File('config/setup'))
        itemLoader.log.info("Starting Cinnamon setup.")
        itemLoader.setup()
        itemLoader.log.info("Setup is finished.")
    }

}
