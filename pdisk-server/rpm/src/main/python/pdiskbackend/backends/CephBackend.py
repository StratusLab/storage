
import os.path
import random

from pdiskbackend.utils import debug
from pdiskbackend.backends.Backend import Backend

class CephBackend(Backend):
    """
    This backend manages a Ceph storage cluster. The proxy server and the
    hypervisors must have the appropriated Ceph configuration and tools.
    """
    
    rbd_bin = '/usr/bin/rbd'
    rbd_default_options = ['--no-progress', '--id', '%%IDENTITY%%']
    rbd_bin_with_options = [rbd_bin] + rbd_default_options
    rbd_dev_path = '/dev/rbd'
    
    lun_backend_cmd_mapping = {
      'check': ['info'],
      'create': ['create'],
    
      # A RBD image cannot be removed if it has snapshots. So, the base snapshot
      # is unprotected then removed before deletion. These two operations are
      # done whatever the image type (origin, userdisk, vmdisk). But snapshotting
      # is relevant only for origin images. That's why their returns must be
      # ignored by setting properly the backend failure mechanism.
      'delete': ['snap_unprotect', 'snap_rm', 'rm'],
    
      # In order to be compliant with other backends, the TURL should match the
      # following pattern: "<protocol>://<server>[:<port>]/<image>". For RBD
      # backend, the protocol is "rbd", the server and port are those of a Ceph
      # monitor service and the image is the fullname of the RBD image.
      'getturl': ['getturl'],
    
      # This action is not a RBD map operation.
      'map': [],
    
      'rebase': ['flatten'],
    
      # There is no RBD operation which only returns the image size. So, the
      # success message mechanism must be used to filter the output of the info
      # operation.
      'size': ['size'],
    
      # A StratusLab snapshot is a clone operation in RBD. To create one, the
      # base image must be snapshotted and protected. These operations fail if
      # the snapshot already exists or is already protected. So, their returns
      # must be ignored by setting properly the backend failure mechanism. By the
      # way, the clone operation will fail itself if the requirements are not
      # fulfilled.
      'snapshot': ['snap_create', 'snap_protect', 'clone'],
    
      # This operation is not a RBD unmap operation.
      'unmap': [],
    }
    
    # In RBD command, an image can be specified either in a long format "--pool
    # <pool> --snap <snapshot> <image>" or in a short format
    # "<pool>/<image>@<snapshot>".
    backend_cmds = {
      'clone': rbd_bin_with_options + ['clone', '%%SRC_POOL%%/%%SRC_IMAGE%%@%%SNAPSHOT_NAME%%', '%%DEST_POOL%%/%%DEST_IMAGE%%'],
      'create': rbd_bin_with_options + ['create', '--size', '%%IMAGE_SIZE%%', '--image-format', '%%IMAGE_FORMAT%%', '%%POOL_NAME%%/%%IMAGE_NAME%%'],
      'flatten': rbd_bin_with_options + ['flatten', '%%POOL_NAME%%/%%IMAGE_NAME%%'],
      'getturl': ['/bin/echo', 'rbd://%%MONITOR%%/%%POOL_NAME%%/%%IMAGE_NAME%%'],
      'info': rbd_bin_with_options + ['info', '%%POOL_NAME%%/%%IMAGE_NAME%%'],
      'rm': rbd_bin_with_options + ['rm', '%%POOL_NAME%%/%%IMAGE_NAME%%'],
      'size': rbd_bin_with_options + ['info', '--format=xml', '%%POOL_NAME%%/%%IMAGE_NAME%%'],
      'snap_create': rbd_bin_with_options + ['snap', 'create', '%%POOL_NAME%%/%%IMAGE_NAME%%@%%SNAPSHOT_NAME%%'],
      'snap_protect': rbd_bin_with_options + ['snap', 'protect', '%%POOL_NAME%%/%%IMAGE_NAME%%@%%SNAPSHOT_NAME%%'],
      'snap_rm': rbd_bin_with_options + ['snap', 'rm', '%%POOL_NAME%%/%%IMAGE_NAME%%@%%SNAPSHOT_NAME%%'],
      'snap_unprotect': rbd_bin_with_options + ['snap', 'unprotect', '%%POOL_NAME%%/%%IMAGE_NAME%%@%%SNAPSHOT_NAME%%'],
    
      # Below other RBD operations which are not currently useful in this
      # backend.
      'bench_write': [],
      'children': rbd_bin_with_options + ['children', '%%POOL_NAME%%/%%IMAGE_NAME%%@%%SNAPSHOT_NAME%%'],
      'cp': rbd_bin_with_options + ['cp', '%%SRC_POOL%%/%%SRC_IMAGE%%', '%%DEST_POOL%%/%%DEST_IMAGE%%'],
      'diff': [],
      'export': [],
      'export-diff': [],
      'import': [],
      'import-diff': [],
      'lock_add': [],
      'lock_list': [],
      'lock_remove': [],
      'ls': rbd_bin_with_options + ['ls', '--long', '%%POOL_NAME%%'],
      'map': rbd_bin_with_options + ['map', '%%POOL_NAME%%/%%IMAGE_NAME%%'],
      'mv': rbd_bin_with_options + ['mv', '%%POOL_NAME%%/%%SRC_IMAGE%%', '%%POOL_NAME%%/%%DEST_IMAGE%%'],
      'resize': rbd_bin_with_options + ['resize', '--size', '%%SIZE%%', '%%POOL_NAME%%/%%IMAGE_NAME%%'],
      'showmapped': rbd_bin_with_options + ['showmapped'],
      'snap_ls': rbd_bin_with_options + ['snap', 'ls', '%%POOL_NAME%%/%%IMAGE_NAME%%'],
      'snap_purge': rbd_bin_with_options + ['snap', 'purge', '%%POOL_NAME%%/%%IMAGE_NAME%%'],
      'snap_rollback': rbd_bin_with_options + ['snap', 'rollback', '%%POOL_NAME%%/%%IMAGE_NAME%%@%%SNAPSHOT_NAME%%'],
      'unmap': rbd_bin_with_options + ['unmap', '%%DEVICE_NAME%%'],
    }
    
    backend_failure_cmds = {
      # Do not raise an error whether a snapshot already exists or already
      # protected before creating a RBD clone. At worst, the clone creation
      # itself will fail.
      'snap_create': {'snapshot': None},
      'snap_protect': {'snapshot': None},
      # Do not raise an error whether a snapshot doesn't exist or is not
      # protected before deleting a RBD image. At worst, the image deletion
      # itself will fail.
      'snap_rm': {'delete': None},
      'snap_unprotect': {'delete': None},
    }
    
    success_msg_pattern = {
      'getturl': '(rbd://.*:.*/.*/.*)',
      # The image size is extracted from the XML output of the info operation.
      # FIXME: size given in bytes instead of gigabytes.
      'size': '.*<size>([0-9]+)</size>.*',
    }
    
    def __init__(self, proxyHost, mgtUser, mgtPrivKey, monitors=None, identity='cloud', 
                 poolName='cloud', snapshotName='base'):
        super(CephBackend, self).__init__()

        self.proxyHost = proxyHost
        self.mgtUser = mgtUser
        self.mgtPrivKey = mgtPrivKey
        
        if self.mgtUser and self.mgtPrivKey:
            debug(1, 'SSH will be used to connect to Ceph backend')
            self.cmd_prefix = self.ssh_cmd_prefix
        else:
            self.cmd_prefix = []
        
        # If no Ceph monitors are provided, use proxy host and default monitor port.
        self.monitors = monitors or ['%s:%s' % (proxyHost, '6789')]
        
        # In order to interact with Ceph cluster, the user executing backend
        # commands must be authenticate. The keyring file of the identity must be
        # available on the proxy.
        self.identity = identity
        
        # All images are stored in a single configurable Ceph pool. So, source and
        # destination pools used in several RBD operations refer to the same pool.
        self.poolName = poolName
        
        # In Ceph, pool name and image name are required to access a snapshot. This
        # backend assumes that images can only have one base snapshot in StratusLab
        # cloud. Thus, snapshots can be access with a pool name, an image name and
        # a fixed snapshot name.
        self.snapshotName = snapshotName
        
        # The default RBD image format doesn't support advanced features like
        # cloning. That's why the new image format is used by default in this
        # backend.
        self.imageFormat = '2'
    
    def buildImageDeviceTemplate(self, withSnapshot=False):
        """ Build the device path template of the image from a predefined format. """
        fullname = self.buildImageFullnameTemplate(withSnapshot)
        return os.path.join(self.rbd_dev_path, fullname)
    
    def buildImageFullnameTemplate(self, withSnapshot=False):
        """
        Build the image fullname template which can be a simple image or a
        snapshot.
        """
        fullname = '%%POOL_NAME%%/%%IMAGE_NAME%%'
        if withSnapshot:
            fullname = fullname + '@%%SNAPSHOT_NAME%%'
        return fullname
    
    def getRandomMonitor(self):
        """ Return a random monitor from the monitors list. """
        selected = random.randint(0, len(self.monitors) - 1)
        return self.monitors[selected]
    
    def getType(self):
        """ Return the name of this backend. """
        return 'Ceph'
    
    def parse(self, string):
        """ Replace variable tags in a string by their appropriated values. """
        
        string = string.replace('%%DEVICE_NAME%%', self.buildImageDeviceTemplate())
        
        string = string.replace('%%MONITOR%%', self.getRandomMonitor())
        string = string.replace('%%IDENTITY%%', self.identity)
        
        string = string.replace('%%POOL_NAME%%', self.poolName)
        string = string.replace('%%SRC_POOL%%', self.poolName)
        string = string.replace('%%DEST_POOL%%', self.poolName)
        
        string = string.replace('%%IMAGE_NAME%%', '%%UUID%%')
        string = string.replace('%%SRC_IMAGE%%', '%%UUID%%')
        string = string.replace('%%DEST_IMAGE%%', '%%SNAP_UUID%%')
        string = string.replace('%%IMAGE_SIZE%%', '%%SIZE_MB%%')
        string = string.replace('%%IMAGE_FORMAT%%', self.imageFormat)
        
        string = string.replace('%%SNAPSHOT_NAME%%', self.snapshotName)
        
        return Backend.detokenize(self, string)
