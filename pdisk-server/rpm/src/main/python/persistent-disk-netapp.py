#!/usr/bin/env python
"""
 Created as part of the StratusLab project (http://stratuslab.eu),
 co-funded by the European Commission under the Grant Agreement
 INSFO-RI-261552.

 Copyright (c) 2011, Centre National de la Recherche Scientifique (CNRS)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
"""

"""
Script used by StratusLab pdisk to manage iSCSI LUNs
"""

__version__ = "1.2.1-1"
__author__  = "Michel Jouvin <jouvin@lal.in2p3.fr>"

import sys
import os
import os.path
import re
from subprocess import *
import StringIO
from optparse import OptionParser
import logging
import logging.handlers
import syslog
import ConfigParser

# Initializations
verbosity = 0
logger = None
action_default = ''
status = 0           # Assume success

# Supported iSCSI proxy variants
iscsi_supported_variants = [ 'lvm', 'netapp' ]

# Keys are supported actions, values are the number of arguments required for the each action
valid_actions = { 'check':1, 'create':2, 'delete':1, 'rebase':1, 'snapshot':3 , 'getturl':1}
valid_actions_str = ', '.join(valid_actions.keys())

config_file_default = '/opt/stratuslab/etc/persistent-disk-backend.conf'
config_main_section = 'main'
config_defaults = StringIO.StringIO("""
# Options commented out are configuration options available for which no 
# sensible default value can be defined.
[main]
# Define the list of iSCSI proxies that can be used.
# One section per proxy must also exists to define parameters specific to the proxy.
#iscsi_proxies=netapp.example.org
# Log file for persistent disk management
log_file=/var/log/stratuslab-persistent-disk.log
# User name to use to connect the filer (may also be defined in the filer section)
mgt_user_name=root
# SSH private key to use for 'mgt_user_name' authorisation
#mgt_user_private_key=/some/file.rsa

#[netapp.example.org]
# iSCSI back-end type (case insensitive)
#type=NetApp
# Initiator group the LUN must be mapped to
#initiator_group = linux_servers
# Name appended to the volume name to build the LUN path (a / will be appended)
#lun_namespace=stratuslab
# Volume name where LUNs will be created
#volume_name = /vol/iscsi
# Name prefix to use to build the volume snapshot used as a LUN clone snapshot parent
# (a _ will be appended)
#volume_snapshot_prefix=pdisk_clone

#[lvm.example.org]
# iSCSI back-end type (case insensitive)
#type=LVM
# LVM volume group to use for creating LUNs
#volume_name = /dev/iscsi.01

""")


####################################################################
# Superclass describing common aspect of every iSCSI backends      #
# Inherited by all iSCSI backend classes.                          #
# Variable required in all backend implementationss are documented #
# here but generally have empty defaults.                          #
####################################################################

class Backend:
  # Command prefix to use to connect through ssh
  ssh_cmd_prefix = [ 'ssh', '-x', '-i', '%%PRIVKEY%%','%%ISCSI_PROXY%%' ]

  # Table defining mapping of LUN actions to NetApp actions.
  # This is a 1 to n mapping (several NetApp commands may be needed for one LUN action).
  # map and unmap are necessary as separate actions as they are not necessarily executed on
  # the same LUN as the other operations (eg. snapshot action).
  # Special values for commands are:
  #  - None: action is not implemented
  #  - empty list: action does nothing
  lun_backend_cmd_mapping = {'check':None,
                             'create':None,
                             'delete':None,
                             'map':None,
                             'rebase':None,
                             'size':None,
                             'snapshot':None,
                             'unmap':None,
                             'getturl':None,
                            }
  
  # Definitions of NetApp commands used to implement actions.
  backend_cmds = {
                  }

  # Commands to execute when a given backend command fails.
  # This is a dictionnary where key is one of the backend_cmds key and the value is another dictionnary
  # whose key is the name of lun action (as defined in lun_backend_cmd_mapping) that defines the
  # context of the backend command and the value is the key of another command in backend_cmds).
  # IF the context is '-', the alternate command will be executed in any context (lun actions) in case of errors.
  # If the value (alternate command) is an empty string, further backend commands part of the same LUN action are skipped.
  # If it is None,processing of further actions contnues as if there was no entry for the command in the dictionnary.
  # IF a backup command fails and has no entry in this dictionnary, the execution continues
  # with next command if any.
  backend_failure_cmds = {
                          }

  # Most commands are expected to return nothing when they succeeded. The following
  # dictionnary lists exceptions and provides a pattern matching output in case of
  # success.
  # Keys must match an existing key in backend_cmds
  success_msg_pattern = {
                        }
  
  # The creation of a new LUN may be required by some operations
  # on some backends (e.g. rebase with LVM backend).
  # This dictionnary allows to define which LUN actions (same keys
  # as in lun_backend_cmd_mapping, value ignored).
  # By default, this variable is empty: redefine it appropriately in
  # the context of a particular backend if needed.
  new_lun_required = {
                      }

  # Generator function returning:
  #    - the command corresponding to the action as a list of tokens, with iSCSI proxy related
  #      variables parsed.
  #    - the expected message patterns in case of success if the command output is not empty. This is returned as
  #      a list of patterns (a simple string is converted to a list).
  # This function must be called from an iteration loop control statement
  def getCmd(self,lun_action):
    if lun_action in self.lun_backend_cmd_mapping:
      backend_actions = self.lun_backend_cmd_mapping[lun_action]
    else:
      abort("Internal error: LUN action '%s' unknown" % (lun_action))

    # If None, means that the action is not implemented
    if backend_actions == None:
      yield backend_actions,None,None
          
    # Intialize parsed_command and success_patters in case backend_actions is an empty list
    parsed_command = []
    success_patterns = None
    failure_command = None
    for action in backend_actions:
      if action in self.backend_cmds.keys():
        parsed_command = self.parse(self.backend_cmds[action])
      else:
        abort("Internal error: action '%s' unknown" % (action))
  
      if action in self.success_msg_pattern:
        success_patterns = self.success_msg_pattern[action]
        if isinstance(success_patterns,str):
          success_patterns = [ success_patterns ]
      else:
        success_patterns = None
        
      if action in self.backend_failure_cmds.keys():
        failure_actions = self.backend_failure_cmds[action]
        if lun_action in failure_actions:
          command = failure_actions[lun_action]
        # '-' is a special key value meaning the alternate command applies to all LN actions
        elif '-' in failure_actions:
          command = failure_actions['-']
        else:
          command = None
        if command:  
          failure_command = self.parse(command)

      yield parsed_command,success_patterns,failure_command
    
  # Method returning true if creation of a new LUN is required for a particular LUN action.
  # LUN creation is the responsibility of the caller.
  def newLunRequired(self,action):
    if action in self.new_lun_required:
      return True
    else:
      return False
  
  
############################################
# Class describing a NetApp iSCSI back-end #
############################################

class NetAppBackend(Backend):
  # The following variables define which command to execute for each action.
  # They are documented in the superclass Backend.
  
  lun_backend_cmd_mapping = {'check':['check'],
                             'create':['create','map'],
                             # Attemtp to delete volume snapshot associated with the LUN if it is no longer used (no more LUN clone exists)
                             'delete':['unmap','delete','snapdel'],
                             'map':['map'],
                             'rebase':[],
                             'size':None,
                             'snapshot':['snapshot','clone'],
                             'unmap':['unmap'],
                             'getturl':[],
                             }
  
  backend_cmds = {'check':[ 'lun', 'show', '%%NAME%%' ],
                  'clone':[ 'lun', 'clone', 'create', '%%SNAP_NAME%%', '-b', '%%NAME%%', '%%SNAP_PARENT%%'  ],
                  'create':[ 'lun', 'create', '-s', '%%SIZE%%g', '-t', '%%LUNOS%%', '%%NAME%%' ],
                  'delete':[ 'lun', 'destroy', '%%NAME%%' ],
                  'map':[ 'lun', 'map', '-f', '%%NAME%%', '%%INITIATORGRP%%' ],
                  'snapdel':[ 'snap', 'delete', '%%VOLUME_NAME%%', '%%SNAP_PARENT%%' ],
                  'snapshot':[ 'snap', 'create', '%%VOLUME_NAME%%', '%%SNAP_PARENT%%'  ],
                  'unmap':[ 'lun', 'unmap', '%%NAME%%', '%%INITIATORGRP%%' ]
                  }

  backend_failure_cmds = {
                          }

  success_msg_pattern = { 'check':'online',
                          # snapdel is expected to fail if there is still a LUN clone using it or if the snapshot doesnt exist
                          # (LUN never cloned or is a clone). These are not considered as an error.
                          'snapdel':[ 'deleting snapshot\.\.\.', 'Snapshot [\w\-]+ is busy because of LUN clone','No such snapshot' ],
                          'snapshot':['^creating snapshot','^Snapshot already exists.']
                        }

  # Would be great to have it configurable as NetApp needs to know the client OS
  lunOS = 'linux'
  
  def __init__(self,proxy,mgtUser,mgtPrivKey,volume,namespace,initiatorGroup,snapshotPrefix):
    self.proxyHost = proxy
    self.mgtUser = mgtUser
    self.mgtPrivKey = mgtPrivKey
    self.volumePath = volume
    self.volumeName = volume.split('/')[-1]
    self.namespace = "%s/%s" % (self.volumePath,namespace)
    self.initiatorGroup = initiatorGroup
    self.snapshotPrefix = snapshotPrefix
    # Command to connect to NetApp filer (always ssh)
    self.cmd_prefix = self.ssh_cmd_prefix
  


  # Add command prefix and parse all variables related to iSCSI proxy in the command (passed as a list of tokens).
  # Return parsed command as a list of token.
  def parse(self,command):    
    # Build command to execute
    action_cmd = []
    action_cmd.extend(self.cmd_prefix)
    action_cmd.extend(command)
    for i in range(len(action_cmd)):
      if action_cmd[i] == '%%INITIATORGRP%%':
        action_cmd[i] = self.initiatorGroup
      elif action_cmd[i] == '%%LUNOS%%':
        action_cmd[i] = self.lunOS
      elif action_cmd[i] == '%%PRIVKEY%%':
        action_cmd[i] = self.mgtPrivKey
      elif action_cmd[i] == '%%ISCSI_PROXY%%':
        action_cmd[i] = "%s@%s" % (self.mgtUser,self.proxyHost)
      elif action_cmd[i] == '%%SNAP_PARENT%%':
        action_cmd[i] = self.snapshotPrefix + '_%%UUID%%'
      elif action_cmd[i] == '%%NAME%%':
        action_cmd[i] = self.namespace + "/%%UUID%%"
      elif action_cmd[i] == '%%SNAP_NAME%%':
        action_cmd[i] = self.namespace + "/%%SNAP_UUID%%"
      elif action_cmd[i] == '%%VOLUME_NAME%%':
        action_cmd[i] = self.volumeName
    return action_cmd
    
  # Return iSCSI back-end type
  def getType(self):
    return 'NetApp'

####################################
# Class describing a File back-end #
####################################

class FileBackend(Backend):
  # The following variables define which command to execute for each action.
  # They are documented in the superclass Backend

  lun_backend_cmd_mapping = {'check':['check'],
                             'create':['create'],
                             'delete':['delete'],
                             'map':[],
                             'rebase':[],
                             'size':[],
                             'snapshot':['copy'],
                             'unmap':[],
                             'getturl':['getturl'],
                             }

  backend_cmds = {'check':['/usr/bin/test','-f','%%LOGVOL_PATH%%'],
                  'create':['/bin/dd','if=/dev/zero','of=%%LOGVOL_PATH%%','bs=1024','count=%%SIZE%%M'],
                  'delete':['/bin/rm','-rf','%%LOGVOL_PATH%%'],
                  'copy':['/bin/cp','%%NEW_LOGVOL_PATH%%','%%LOGVOL_PATH%%'],
                  'getturl':['/bin/echo','file://%%LOGVOL_PATH%%'],
                  }

  success_msg_pattern = {'create' : '.*',
                         'getturl' : '(.*://.*)',
                         }
  def __init__(self,proxy,volume,mgtUser=None,mgtPrivKey=None):
    self.volumeName = volume
    self.proxyHost  = proxy
    self.mgtUser    = mgtUser
    self.mgtPrivKey = mgtPrivKey
    if self.mgtUser and self.mgtPrivKey:
      debug(1,'SSH will be used to connect to File backend')
      self.cmd_prefix = self.ssh_cmd_prefix
    else:
      self.cmd_prefix = []

  # Add command prefix and parse all variables related to iSCSI proxy in the command (passed as a list of tokens).
  # Return parsed command as a list of token.
  def parse(self,command):    
    # Build command to execute
    action_cmd = []
    action_cmd.extend(self.cmd_prefix)
    action_cmd.extend(command)
    for i in range(len(action_cmd)):
      if action_cmd[i] == '%%ISCSI_PROXY%%':
        action_cmd[i] = "%s@%s" % (self.mgtUser,self.proxyHost)
      elif re.search('%%LOGVOL_PATH%%',action_cmd[i]):
        action_cmd[i] = re.sub('%%LOGVOL_PATH%%',self.volumeName+"/%%UUID%%",action_cmd[i])
      elif re.search('%%NEW_LOGVOL_PATH%%',action_cmd[i]):
        action_cmd[i] = re.sub('%%NEW_LOGVOL_PATH%%',self.volumeName+"/%%SNAP_UUID%%",action_cmd[i])
      elif action_cmd[i] == '%%PRIVKEY%%':
        action_cmd[i] = self.mgtPrivKey
      elif action_cmd[i] == '%%VOLUME_NAME%%':
        action_cmd[i] = self.volumeName
    return action_cmd

  def getType(self):
    return 'File'

#########################################
# Class describing a LVM iSCSI back-end #
#########################################

class LVMBackend (Backend):
  # The following variables define which command to execute for each action.
  # They are documented in the superclass Backend.
  
  lun_backend_cmd_mapping = {'check':['check'],
                             'create':['create','add_device','reload_iscsi'],
                             'delete':['remove_device', 'reload_iscsi','remove'],
                             # map is a required action for snapshot action but does nothing in LVM
                             'map':['add_device','reload_iscsi'],
                             'rebase':['rebase','add_device','reload_iscsi'],
                             'size':['size'],
                             'snapshot':['snapshot','add_device','reload_iscsi'],
                             'unmap':['remove_device','reload_iscsi'],
                             'getturl' : ['getturl'],
                             }
  
  backend_cmds = {'check':[ '/usr/bin/test', '-b', '%%LOGVOL_PATH%%' ],
                  'create':[ '/sbin/lvcreate', '-L', '%%SIZE%%G', '-n', '%%UUID%%', '%%VOLUME_NAME%%' ],
                  'dmremove':[ '/sbin/dmsetup', 'remove', '%%DM_VOLUME_PATH%%' ],
                  'add_device':[ '/bin/sed', '-i', '1i<target iqn.2011-01.eu.stratuslab:%%UUID%%> \\n backing-store %%LOGVOL_PATH%% \\n </target>','/etc/stratuslab/iscsi.conf' ],
                  'reload_iscsi': [ '/usr/sbin/tgt-admin','--update','ALL'],
                  'remove_device': [ '/bin/sed', '-i', '/<target iqn.2011-01.eu.stratuslab:.*%%UUID%%.*/,+2d', '/etc/stratuslab/iscsi.conf' ],
                  'remove':[ '/sbin/lvremove', '-f', '%%LOGVOL_PATH%%' ],
                  'rebase':[ '/bin/dd', 'if=%%LOGVOL_PATH%%', 'of=%%NEW_LOGVOL_PATH%%'],
                  # lvchange doesn't work with clone. Normally unneeded as lvremove -f (remove) does the same
                  'setinactive':[ '/sbin/lvchange', '-a', 'n', '%%LOGVOL_PATH%%' ],
                  'size':['/sbin/lvs', '-o', 'lv_size', '--noheadings', '%%LOGVOL_PATH%%'],
                  'snapshot':[ '/sbin/lvcreate', '--snapshot', '-p', 'rw', '--size', '%%SIZE%%G', '-n', '%%UUID%%', '%%NEW_LOGVOL_PATH%%'  ],
                  'getturl' : ['/bin/echo', 'iscsi://%%PORTAL%%/iqn.2011-01.eu.stratuslab:%%UUID%%:1' ],
                  }
  
  backend_failure_cmds = {'remove': {'add_device' : [ 'sed', '-i', '1i<target iqn.2011-01.eu.stratuslab:%%UUID%%> \\n backing-store %%LOGVOL_PATH%% \\n </target>','/etc/stratuslab/iscsi.conf' ]}
                                    #{'delete' : 'add_device'}
                          }

  success_msg_pattern = {'create':'Logical volume "[\w\-]+" created',
                         'remove':'Logical volume "[\w\-]+" successfully removed',
                         'rebase':'\d+ bytes .* copied',
                         'setinactive':[ '^$', 'File descriptor .* leaked on lvchange invocation' ],
                         'size':['([\d\.]+)g'],
                         'snapshot':'Logical volume "[\w\-]+" created',
                         'getturl' : '(.*://.*/.*)'
                        }
  
  new_lun_required = {'rebase':True
                      }
  
  
  def __init__(self,proxy,volume,mgtUser=None,mgtPrivKey=None):
    self.volumeName = volume
    self.proxyHost = proxy
    self.mgtUser = mgtUser
    self.mgtPrivKey = mgtPrivKey
    if self.mgtUser and self.mgtPrivKey:
      debug(1,'SSH will be used to connect to LVM backend')
      self.cmd_prefix = self.ssh_cmd_prefix
    else:
      self.cmd_prefix = [ ]
  

  # Add command prefix and parse all variables related to iSCSI proxy in the command (passed as a list of tokens).
  # Return parsed command as a list of token.
  def parse(self,command):    
    # Build command to execute
    action_cmd = []
    action_cmd.extend(self.cmd_prefix)
    action_cmd.extend(command)
    for i in range(len(action_cmd)):
      if action_cmd[i] == '%%ISCSI_PROXY%%':
        action_cmd[i] = "%s@%s" % (self.mgtUser,self.proxyHost)
      elif re.search('%%LOGVOL_PATH%%',action_cmd[i]):
        action_cmd[i] = re.sub('%%LOGVOL_PATH%%',self.volumeName+"/%%UUID%%",action_cmd[i])
      elif re.search('%%NEW_LOGVOL_PATH%%',action_cmd[i]):
        action_cmd[i] = re.sub('%%NEW_LOGVOL_PATH%%',self.volumeName+"/%%SNAP_UUID%%",action_cmd[i])
      elif action_cmd[i] == '%%PRIVKEY%%':
        action_cmd[i] = self.mgtPrivKey
      elif action_cmd[i] == '%%VOLUME_NAME%%':
        action_cmd[i] = self.volumeName
      elif re.search('%%PORTAL%%',action_cmd[i]):
        action_cmd[i] = re.sub('%%PORTAL%%',self.proxyHost+":3260",action_cmd[i])
    return action_cmd
    
  # Return iSCSI back-end type
  def getType(self):
    return 'LVM'


#################################################################
# Class describing a LUN and implementing the supported actions #
#################################################################

class LUN:

  # Some LUN commands (e.g. rebase) needs to return information as a string on stdout
  # that will be captured by pdisk. The string is defined using the same tokens as commands.
  # By default, a command returns nothing on stdout.
  # This information is returned only on successful execution of action.
  action_output = {'rebase':'%%SNAP_UUID%%',
                   }
  
  def __init__(self,uuid,size=None,proxy=None):
    self.uuid = uuid
    self.size = size
    self.proxy = proxy
    # Another LUN involved in actions like rebase or snapshot
    self.associatedLUN = None
    
  def getUuid(self):
    return self.uuid
  
  def check(self):
    status,optInfo = self.__executeAction__('check')
    return status
    
  def create(self):
    status,optInfo = self.__executeAction__('create')
    return status
    
  def delete(self):
    status,optInfo = self.__executeAction__('delete')
    return status
    
  def getSize(self):
    status,self.size = self.__executeAction__('size')
    if status != 0:
      abort('Failure to retrieve size of LUN %s' % (self.uuid))
    return status

  def getTurl(self):
    status,self.turl = self.__executeAction__('getturl')
    if status != 0:
      abort('Failure to retrieve Transport URL of %s' % (self.uuid))
    return self.turl
    
  def map(self):
    status,optInfo = self.__executeAction__('map')
    return status
    
  def rebase(self):
    if self.proxy.newLunRequired('rebase'):
      #TODO: generate a UUID based on creation timestamp as in PDisk
      new_lun_uuid = self.getUuid() + '_rebased'
      self.getSize()
      self.associatedLUN = LUN(new_lun_uuid,size=self.size,proxy=self.proxy)
      if self.associatedLUN.create() != 0:
        abort('An error occured creating a new LUN for rebasing %s' % (self.uuid))
    else:
      self.associatedLUN = self     # To simplify returned value
    status,optInfo = self.__executeAction__('rebase')
    return status
    
  def snapshot(self,snapshot_lun):
    self.associatedLUN = snapshot_lun
    status,optInfo = self.__executeAction__('snapshot')
    return status
    
  def unmap(self):
    status,optInfo = self.__executeAction__('unmap')
    return status
    
    
  # Execute an action on a LUN.
  # An action may involve several actual commands : getCmd() method of proxy is a generator returning
  # the commands to execute one by one.
  # In case an error occurs during one command, try to continue...
  # Return the status of the last command executed and an optional additional value returned by the command.
  # Optionally a string is printed on stdout to allow the script to return information to the caller.
  # Special values for commands are:
  #  - None: action is not implemented
  #  - empty list: action does nothing  
  def __executeAction__(self,action):
    status = 0         # Assume success
    optInfo = None
    for cmd_toks,successMsg,failure_cmd_toks in self.proxy.getCmd(action):
      # When returned command for action is None, it means that the action is not implemented
      if cmd_toks == None:
        abort("Action '%s' not implemented by SCSI back-end type '%s'" % (action,self.proxy.getType()))
      command = Command(action,self.parseCmd(cmd_toks),successMsg)
      command.execute()
      status,optInfo = command.checkStatus()
      if status != 0 and failure_cmd_toks:
        command = Command(action,self.parseCmd(failure_cmd_toks),successMsg)
        command.execute()
        status,optInfo = command.checkStatus()        
        break
      # If failure_cmd_toks is an amtpy string, stop LUN action processing
      elif failure_cmd_toks == '':
        break
      
    if status == 0 and action in self.action_output:
      print self.__parse__(self.action_output[action])
      
    return status,optInfo

  # Parse all variables related to current LUN in the command (passed and returned as a list of tokens).  
  def parseCmd(self,action_cmd):
    for i in range(len(action_cmd)):
      action_cmd[i] = self.__parse__(action_cmd[i])
    return action_cmd
  
  # Do the actual string parsing
  def __parse__(self,string):
    if re.search('%%SIZE%%',string):
      string = re.sub('%%SIZE%%',self.size,string)
    elif re.search('%%UUID%%',string):
      string = re.sub('%%UUID%%',self.getUuid(),string)
    elif re.search('%%SNAP_UUID%%',string):
      string = re.sub('%%SNAP_UUID%%',self.associatedLUN.getUuid(),string)
    return string
    

#######################################################
# Class representing a command passed to the back-end #
#######################################################
    
class Command:
  cmd_output_start = '<<<<<<<<<<'
  cmd_output_end = '>>>>>>>>>>'
  
  def __init__(self,action,cmd,successMsgs=None):
    self.action = action
    self.action_cmd = cmd
    self.successMsgs = successMsgs
    self.proc = None

  def execute(self):
    status = 0
    # Execute command: NetApp command don't return an exit code. When a command is sucessful,
    # its output is empty.
    debug(1,"Executing command: '%s'" % (' '.join(self.action_cmd)))
    try:
      self.proc = Popen(self.action_cmd, shell=False, stdout=PIPE, stderr=STDOUT)
    except OSError, details:
      abort('Failed to execute %s action: %s' % (self.action,details))
      status = 1
    return status
  
  def checkStatus(self):
    optInfo = None
    try:
      retcode = self.proc.wait()
      output = self.proc.communicate()[0]
      if retcode != 0:
          abort('An error occured during %s action (error=%s). Command output:\n%s\n%s\n%s' % (self.action,retcode,self.cmd_output_start,output,self.cmd_output_end))
      else:
          # Need to check if the command is expected to return an output when successfull
          success = False
          if self.successMsgs:
            for successPattern in self.successMsgs:
              output_regexp = re.compile(successPattern)
              matcher = output_regexp.search(output)
              if matcher:
                # Return only the first capturing group
                if output_regexp.groups > 0:
                  optInfo = matcher.group(1)
                success = True
                break
          else:
            if len(output) == 0:
              success = True
          if success:
            debug(1,'%s action completed successfully.' % (self.action))
            if len(output) > 0:
              debug(2,'Command output:\n%s\n%s\n%s' % (self.cmd_output_start,output,self.cmd_output_end))
          else:
            retcode = -1
            debug(0,'An error occured during %s action. Command output:\n%s\n%s\n%s' % (self.action,self.cmd_output_start,output,self.cmd_output_end))
    except OSError, details:
      abort('Failed to execute %s action: %s' % (self.action,details))  
    return retcode,optInfo


###############################
# Functions to handle logging #
###############################

def abort(msg):
    logger.error("Persistent disk operation failed:\n%s" % (msg))
    sys.exit(2)

def debug(level,msg):
  if level <= verbosity:
    if level == 0:
      logger.info(msg)
    else:
      logger.debug(msg)



#############
# Main code #
#############

# Configure loggers and handlers.
# Initially cnfigure only syslog and stderr handler.

logging_source = 'stratuslab-pdisk'
logger = logging.getLogger(logging_source)
logger.setLevel(logging.DEBUG)

#fmt=logging.Formatter("%(asctime)s - %(name)s - %(levelname)s - %(message)s")
fmt=logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
# Handler used to report to SVN must display only the message to allow proper XML formatting
svn_fmt=logging.Formatter("%(message)s")

console_handler = logging.StreamHandler()
console_handler.setLevel(logging.DEBUG)
logger.addHandler(console_handler)


# Parse configuration and options

usage_text = """usage: %prog [options] action_parameters

Parameters:
    action=check:    LUN_UUID
    action=create:   LUN_UUID LUN_Size
    action=delete:   LUN_UUID
    action=rebase:   LUN_UUID (will return the rebased LUN UUID on stdout)
    action=snapshot: LUN_UUID New_LUN_UUID Snapshot_Size
    action=getturl:  LUN_UUID
"""

parser = OptionParser(usage=usage_text)
parser.add_option('--config', dest='config_file', action='store', default=config_file_default, help='Name of the configuration file to use (D: %s)' % (config_file_default))
parser.add_option('--action', dest='action', action='store', default=action_default, help='Action to execute. Valid actions: %s'%(valid_actions_str))
parser.add_option('-v', '--debug', '--verbose', dest='verbosity', action='count', default=0, help='Increase verbosity level for debugging (multiple allowed)')
parser.add_option('--version', dest='version', action='store_true', default=False, help='Display various information about this script')
options, args = parser.parse_args()

if options.version:
  debug (0,"Version %s written by %s" % (__version__,__author__))
  debug (0,__doc__)
  sys.exit(0)

if options.verbosity:
  verbosity = options.verbosity

if options.action in valid_actions:
  if len(args) < valid_actions[options.action]:
    debug(0,"Insufficient argument provided (%d required)" % (valid_actions[options.action]))  
    parser.print_help()
    abort("")
else:
  if options.action:
    debug(0,"Invalid action requested (%s)\n" % (options.action))
  else:
    debug(0,"No action specified\n")
  parser.print_help()
  abort("")
    

# Read configuration file.
# The file must exists as there is no sensible default value for several options.

config = ConfigParser.ConfigParser()
config.readfp(config_defaults)
try:
  config.readfp(open(options.config_file))
except IOError, (errno,errmsg):
  if errno == 2:
    abort('Configuration file (%s) is missing.' % (options.config_file))
  else:
    abort('Error opening configuration file (%s): %s (errno=%s)' % (options.config_file,errmsg,errno))
  
logfile_handler = None
try:
  log_file = config.get(config_main_section,'log_file')
  if log_file:
    logfile_handler = logging.handlers.RotatingFileHandler(log_file,'a',100000,10)
    logfile_handler.setLevel(logging.DEBUG)
    logfile_handler.setFormatter(fmt)
    logger.addHandler(logfile_handler)
except ValueError:
  abort("Invalid value specified for 'log_file' (section %s)" % (config_main_section))

if logfile_handler == None or not log_file:
  # Use standard log destination in case a log file is not defined
  syslog_handler = logging.handlers.SysLogHandler('/dev/log')
  syslog_handler.setLevel(logging.WARNING)
  logger.addHandler(syslog_handler)


try:
  iscsi_proxies_list = config.get(config_main_section,'iscsi_proxies')
  iscsi_proxies = iscsi_proxies_list.split(',')
  iscsi_proxy_name = iscsi_proxies[0]
except ValueError:
  abort("Invalid value specified for 'iscsi_proxies' (section %s) (must be a comma-separated list)" % (config_main_section))

try:
  backend_variant=config.get(iscsi_proxy_name,'type')  
except:
  abort("Section '%s' or required attribute 'type' missing" % (iscsi_proxy_name))

# NetApp back-end configuration

if backend_variant.lower() == 'netapp':
  # Retrieve NetApp back-end mandatory attributes.
  # Mandatory attributes should be defined as keys of backend_attributes with an arbitrary value.
  # Key name must match the attribute name in the configuration file.
  backend_attributes = {'initiator_group':'',
                        'lun_namespace':'',
                        'volume_name':'',
                        'volume_snapshot_prefix':''
                        }
  try:
    for attribute in backend_attributes.keys():
      backend_attributes[attribute]=config.get(iscsi_proxy_name,attribute)
  except:
    abort("Section '%s' or required attribute '%s' missing" % (iscsi_proxy_name,attribute))
  
  try:
    backend_attributes['mgt_user_name']=config.get(iscsi_proxy_name,'mgt_user_name')  
  except:
    try:
      backend_attributes['mgt_user_name']=config.get(config_main_section,'mgt_user_name')  
    except:
      abort("User name to use for connecting to iSCSI proxy undefined")
    
  try:
    backend_attributes['mgt_user_private_key']=config.get(iscsi_proxy_name,'mgt_user_private_key')  
  except:
    try:
      backend_attributes['mgt_user_private_key']=config.get(config_main_section,'mgt_user_private_key')  
    except:
      abort("SSH private key to use for connecting to iSCSI proxy undefined")
  
  # Create iSCSI back-end object  
  iscsi_proxy = NetAppBackend(iscsi_proxy_name,
                              backend_attributes['mgt_user_name'],
                              backend_attributes['mgt_user_private_key'],
                              backend_attributes['volume_name'],
                              backend_attributes['lun_namespace'],
                              backend_attributes['initiator_group'],
                              backend_attributes['volume_snapshot_prefix']
                              )

# LVM back-end configuration
elif backend_variant.lower() == 'lvm':
  # Retrieve NetApp back-end mandatory attributes.
  # Mandatory attributes should be defined as keys of backend_attributes with an arbitrary value.
  # Key name must match the attribute name in the configuration file.
  backend_attributes = {'volume_name':'',
                        }
  try:
    for attribute in backend_attributes.keys():
      backend_attributes[attribute]=config.get(iscsi_proxy_name,attribute)
  except:
    abort("Section '%s' or required attribute '%s' missing" % (iscsi_proxy_name,attribute))

  # 'local' is a reserved name to designate the local machine: in this case, don't use ssh to
  # connect to backend
  if iscsi_proxy_name == 'local':
    backend_attributes['mgt_user_name'] = None
    backend_attributes['mgt_user_private_key'] = None
  else:  
    try:
      backend_attributes['mgt_user_name']=config.get(iscsi_proxy_name,'mgt_user_name')  
    except:
      try:
        backend_attributes['mgt_user_name']=config.get(config_main_section,'mgt_user_name')  
      except:
        abort("User name to use for connecting to iSCSI proxy undefined")
      
    try:
      backend_attributes['mgt_user_private_key']=config.get(iscsi_proxy_name,'mgt_user_private_key')  
    except:
      try:
        backend_attributes['mgt_user_private_key']=config.get(config_main_section,'mgt_user_private_key')  
      except:
        abort("SSH private key to use for connecting to iSCSI proxy undefined")

  # Create iSCSI back-end object  
  iscsi_proxy = LVMBackend(iscsi_proxy_name,
                           backend_attributes['volume_name'],
                           backend_attributes['mgt_user_name'],
                           backend_attributes['mgt_user_private_key'],
                          )

elif backend_variant.lower() == 'file':
  backend_attributes = {'volume_name':'',
                        }
  try:
    for attribute in backend_attributes.keys():
      backend_attributes[attribute]=config.get(iscsi_proxy_name,attribute)
  except:
    abort("Section '%s' or required attribute '%s' missing" % (iscsi_proxy_name,attribute))

  if iscsi_proxy_name == 'local':
    backend_attributes['mgt_user_name'] = None
    backend_attributes['mgt_user_private_key'] = None
  else:
    try:
      backend_attributes['mgt_user_name']=config.get(iscsi_proxy_name,'mgt_user_name')  
    except:
      try:
        backend_attributes['mgt_user_name']=config.get(config_main_section,'mgt_user_name')  
      except:
        abort("User name to use for connecting to iSCSI proxy undefined")
      
    try:
      backend_attributes['mgt_user_private_key']=config.get(iscsi_proxy_name,'mgt_user_private_key')  
    except:
      try:
        backend_attributes['mgt_user_private_key']=config.get(config_main_section,'mgt_user_private_key')  
      except:
        abort("SSH private key to use for connecting to iSCSI proxy undefined")

  # Create iSCSI back-end object
  iscsi_proxy = FileBackend(iscsi_proxy_name,
                            backend_attributes['volume_name'],
                            backend_attributes['mgt_user_name'],
                            backend_attributes['mgt_user_private_key'],
                            )    

  
# Abort if iSCSI back-end variant specified is not supported
else:
   abort("Unsupported iSCSI back-end variant '%s' (supported variants: %s)" % (backend_variant,','.join(iscsi_supported_variants)))   


# Execute requested action

if options.action == 'check':
  debug(1,"Checking LUN existence...")
  lun = LUN(args[0],proxy=iscsi_proxy)
  status = lun.check()
elif options.action == 'create':
  debug(1,"Creating LUN...")
  lun = LUN(args[0],size=args[1],proxy=iscsi_proxy)
  status = lun.create()
elif options.action == 'delete':
  debug(1,"Deleting LUN...")
  lun = LUN(args[0],proxy=iscsi_proxy)
  status = lun.delete()
elif options.action == 'rebase':
  debug(1,"Rebasing LUN...")
  lun = LUN(args[0],proxy=iscsi_proxy)
  status = lun.rebase()
elif options.action == 'snapshot':
  debug(1,"Doing a LUN snapshot...")
  lun = LUN(args[1],size=args[2],proxy=iscsi_proxy)
  snapshot_lun = LUN(args[0],proxy=iscsi_proxy)
  # Only the last error is returned
  status = lun.snapshot(snapshot_lun)
  status = snapshot_lun.map()
elif options.action == 'getturl' :
  debug(1,"Returning Transport URL...")
  lun = LUN(args[0], proxy=iscsi_proxy)
  turl = lun.getTurl()
  if turl == "":
    status = 1
  else:
    status = 0
  print turl
else:
  abort ("Internal error: unimplemented action (%s)" % (options.action))
  
sys.exit(status)
