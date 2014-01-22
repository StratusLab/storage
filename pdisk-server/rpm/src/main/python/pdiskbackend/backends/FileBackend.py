
import re

from pdiskbackend.utils import debug
from .Backend import Backend

####################################
# Class describing a File back-end #
####################################

class FileBackend(Backend):
  # The following variables define which command to execute for each action.
  # They are documented in the superclass Backend

  lun_backend_cmd_mapping = {'check':['check'],
                             'create':['create', 'chown'],
                             'delete':['delete'],
                             'getturl':['getturl'],
                             'map':[],
                             'rebase':[],
                             'size':[],
                             'snapshot':['copy'],
                             'unmap':[],
                             }

  backend_cmds = {'check':['/usr/bin/test', '-f', '%%LOGVOL_PATH%%'],
                  'chown' :['/bin/chown', 'oneadmin:cloud', '%%LOGVOL_PATH%%'],
                  'copy':['/bin/cp', '%%LOGVOL_PATH%%', '%%NEW_LOGVOL_PATH%%'],
                  'create':['/bin/dd', 'if=/dev/zero', 'of=%%LOGVOL_PATH%%', 'bs=1024', 'count=%%SIZE%%M'],
                  'delete':['/bin/rm', '-rf', '%%LOGVOL_PATH%%'],
                  'getturl':['/bin/echo', 'file://%%LOGVOL_PATH%%'],
                  }

  success_msg_pattern = {'create' : '.*',
                         'getturl' : '(.*://.*)',
                         }
  def __init__(self, proxy, volume, mgtUser=None, mgtPrivKey=None):
    self.volumeName = volume
    self.proxyHost = proxy
    self.mgtUser = mgtUser
    self.mgtPrivKey = mgtPrivKey
    if self.mgtUser and self.mgtPrivKey:
      debug(1, 'SSH will be used to connect to File backend')
      self.cmd_prefix = self.ssh_cmd_prefix
    else:
      self.cmd_prefix = []

  # Parse all variables related to iSCSI proxy in the string passed as argument.
  # Return parsed string.
  def parse(self, string):
    if re.search('%%LOGVOL_PATH%%', string):
      string = re.sub('%%LOGVOL_PATH%%', self.volumeName + "/%%UUID%%", string)
    elif re.search('%%NEW_LOGVOL_PATH%%', string):
      string = re.sub('%%NEW_LOGVOL_PATH%%', self.volumeName + "/%%SNAP_UUID%%", string)
    return Backend.detokenize(self, string)

  def getType(self):
    return 'File'
