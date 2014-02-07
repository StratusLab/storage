
from pdiskbackend.backends.Backend import Backend

NETAPP_7MODE = ['netapp', 'netapp-7mode'] # 'netapp' is for backward compatibility.
NETAPP_CLUSTER = ['netapp-cluster']

NETAPP_FLAVOURS = NETAPP_7MODE + NETAPP_CLUSTER

def getNetAppBackend(flavor, proxy, mgtUser, mgtPrivKey, volume, namespace, 
                     initiatorGroup, snapshotPrefix):
    if flavor.lower() in NETAPP_7MODE:
        return NetApp7Mode(proxy, mgtUser, mgtPrivKey, volume, namespace, 
                             initiatorGroup, snapshotPrefix)
    elif flavor.lower() in NETAPP_CLUSTER:
        return NetAppCluster(proxy, mgtUser, mgtPrivKey, volume, namespace, 
                             initiatorGroup, snapshotPrefix)
    else:
        raise Exception('Unknown NetApp flavor provided: %s' % flavor)

############################################
# Class describing a NetApp iSCSI back-end #
############################################

class NetAppBackend(Backend):

    lun_backend_cmd_mapping = {'check':['check'],
                               'create':['create'],
                               # Attemtp to delete volume snapshot associated with the LUN if it is no longer used (no more LUN clone exists)
                               'delete':['delete', 'snapdel'],
                               'getturl':['get_target', 'get_lun'],
                               'map':['map'],
                               'rebase':[],
                               'size':None,
                               'snapshot':['snapshot', 'clone'],
                               'unmap':['unmap'],
                               }

    backend_failure_cmds = {}
    
    failure_ok_msg_pattern = {}
    
    opt_info_format = {'getturl':'iscsi://%%ISCSI_HOST%%/%s:%s'}

    # Would be great to have it configurable as NetApp needs to know the client OS
    lunOS = 'linux'
    
    def __init__(self, proxy, mgtUser, mgtPrivKey, volume, namespace, initiatorGroup, 
                 snapshotPrefix):
        super(NetAppBackend, self).__init__()
        
        self.proxyHost = proxy
        self.mgtUser = mgtUser
        self.mgtPrivKey = mgtPrivKey
        self.volumePath = volume
        self.volumeName = volume.split('/')[-1]
        self.namespace = ("%s/%s" % (self.volumePath.rstrip('/'), namespace)).rstrip('/')
        self.initiatorGroup = initiatorGroup
        self.snapshotPrefix = snapshotPrefix
        # Command to connect to NetApp filer (always ssh)
        self.cmd_prefix = self.ssh_cmd_prefix
        
        self._type = 'netapp'
    
    # Parse all variables related to iSCSI proxy in the string passed as argument.
    # Return parsed string.
    def parse(self, string):
        if string == '%%INITIATORGRP%%':
            string = self.initiatorGroup
        elif string == '%%LUNOS%%':
            string = self.lunOS
        elif string == '%%SNAP_PARENT%%':
            string = self.snapshotPrefix + '_%%UUID%%'
        elif string == '%%NAME%%':
            string = self.namespace + "/%%UUID%%"
        elif string == '%%SNAP_NAME%%':
            string = self.namespace + "/%%SNAP_UUID%%"
        return Backend.detokenize(self, string)
    
    # Return iSCSI back-end type
    def getType(self):
        return self._type


class NetApp7Mode(NetAppBackend):
    # The following variables define which command to execute for each action.
    # They are documented in the superclass Backend.
    
    backend_cmds = {'check':[ 'lun', 'show', '%%NAME%%' ],
                    'clone':[ 'lun', 'clone', 'create', '%%SNAP_NAME%%', '-b', '%%NAME%%', '%%SNAP_PARENT%%'  ],
                    'create':[ 'lun', 'create', '-s', '%%SIZE%%g', '-t', '%%LUNOS%%', '%%NAME%%' ],
                    'delete':[ 'lun', 'destroy', '%%NAME%%' ],
                    'get_lun':[ 'lun', 'show', '-v', '%%NAME%%' ],
                    'get_target':[ 'iscsi', 'nodename' ],
                    'igroup_add':[ 'igroup', 'add', '%%INITIATOR_HOST%%', '%%INITIATOR_ID%%' ],
                    'igroup_create':[ 'igroup', 'create', '-i', '-t', '%%LUNOS%%', '%%INITIATOR_HOST%%' ],
                    'igroup_delete':[ 'igroup', 'delete', '%%INITIATOR_HOST%%' ],
                    'map':[ 'lun', 'map', '-f', '%%NAME%%', '%%INITIATORGRP%%' ],
                    'snapdel':[ 'snap', 'delete', '%%VOLUME_NAME%%', '%%SNAP_PARENT%%' ],
                    'snapshot':[ 'snap', 'create', '%%VOLUME_NAME%%', '%%SNAP_PARENT%%' ],
                    'unmap':[ 'lun', 'unmap', '%%NAME%%', '%%INITIATORGRP%%' ]
                    }
    
    success_msg_pattern = {'check':'online',
                           'get_lun':'Maps:\s+[\w\-]+=(\d+)',
                           'get_target':'iSCSI target nodename:\s+([\w\-:\.]+)',
                           'igroup_create':['','igroup\s+create:\s+Initiator\s+group\s+already\s+exists'],
                           # snapdel is expected to fail if there is still a LUN clone using it or if the snapshot doesnt exist
                           # (LUN never cloned or is a clone). These are not considered as an error.
                           'snapdel':['deleting snapshot\.\.\.', 
                                      'Snapshot [\w\-]+ is busy because of LUN clone',
                                      'No such snapshot'],
                           'snapshot':['^creating snapshot','^Snapshot already exists.']
                          }
    
    def __init__(self, proxy, mgtUser, mgtPrivKey, volume, namespace, initiatorGroup, 
                 snapshotPrefix):
        super(NetApp7Mode, self).__init__(proxy, mgtUser, mgtPrivKey, volume, namespace, initiatorGroup, 
                                          snapshotPrefix)
        self._type = 'netapp-7mode'


class NetAppCluster(NetApp7Mode):

    backend_cmds = NetApp7Mode.backend_cmds.copy()
    backend_cmds.update({'clone':[ 'file', 'clone', 'create', '-destination-path', '%%SNAP_NAME%%', '-source-path', '%%NAME%%', '-s', '%%SNAP_PARENT%%'  ],
                         'get_lun':[ 'lun', 'show', '-v', '-m', '%%NAME%%' ],
                         'get_target':[ 'vserver', 'iscsi', 'show', '-fields', 'target-name']})

    failure_ok_msg_pattern = NetApp7Mode.failure_ok_msg_pattern.copy()
    failure_ok_msg_pattern.update({'destroy':['(^Error: There are no entries matching your query)'],
                                   'delete':['(^Error: There are no entries matching your query)'],
                                   'snapdel':['(^Error: There are no entries matching your query)'],
                                   'map':['(^Error: command failed: LUN already mapped to this group)']})

    success_msg_pattern = NetApp7Mode.success_msg_pattern.copy()
    success_msg_pattern.update({'create':'Created a LUN of size.*',
                  'delete':'.*Error: There are no entries matching your query.*',
                  'map':'.*',
                  'unmap':'.*',
                  'check':'online',
                  'get_lun':'LUN ID:\s+(\d+)',
                  'get_target':'\s+(iqn[\w\-:\.]+)',
                  'igroup_create':['', 'igroup\s+create:\s+Initiator\s+group\s+already\s+exists'],
                  # snapdel is expected to fail if there is still a LUN clone using it or if the snapshot doesnt exist
                  # (LUN never cloned or is a clone). These are not considered as an error.
                  'snapdel':[ 'deleting snapshot\.\.\.', 
                             'Snapshot [\w\-]+ is busy because of LUN clone', 
                             'No such snapshot', 
                             'Error: There are no entries matching your query.'],
                  'snapshot':['^creating snapshot', '^Snapshot already exists.']})

    def __init__(self, proxy, mgtUser, mgtPrivKey, volume, namespace, initiatorGroup, 
                 snapshotPrefix):
        super(NetAppCluster, self).__init__(proxy, mgtUser, mgtPrivKey, volume, 
                                            namespace, initiatorGroup, 
                                            snapshotPrefix)
        self._type = 'netapp-clustered'
