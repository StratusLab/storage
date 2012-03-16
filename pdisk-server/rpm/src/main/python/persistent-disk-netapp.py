#!/usr/bin/env python
"""
Script to manage a iSCSI LUN on a NetApp filer
"""

__version__ = "0.0.9-1dev"
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

# Keys are supported actions, values are the number of arguments required for the each action
valid_actions = { 'check':1, 'create':2, 'delete':1, 'rebase':3, 'snapshot':2 }
valid_actions_str = ', '.join(valid_actions.keys())

config_file_default = '/opt/stratuslab/etc/persistent-disk-backend.conf'
config_main_section = 'main'
config_defaults = StringIO.StringIO("""
# Options commented out are configuration options available for which no 
# sensible default value can be defined.
[main]
# Define the list of iSCSI proxies that can be used.
# One section per proxy must also exists to define parameters specific to the proxy.
#iscsi_proxies=filer.example.org
# Log file for persistent disk management
log_file=/var/log/stratuslab-persistent-disk.log
# User name to use to connect the filer (may also be defined in the filer section)
mgt_user_name=root
# SSH private key to use for 'mgt_user_name' authorisation
#mgt_user_private_key=/some/file.rsa

#[filer.example.org]
# LUN name prefix
#lun_namespace=/iscsi/stratuslab
# Initiator group the LUN must be mapped to
#initiator_group = linux_servers

""")


class NetAppProxy:
  lun_cmd_prefix = [ 'ssh', '-x', '-i', '%%PRIVKEY%%','%%ISCSI_PROXY%%' ]
  lun_cmds = {'check':[ 'lun', 'show', '%%NAME%%' ],
              'create':[ 'lun', 'create', '-s', '%%SIZE%%', '-t', '%%LUNOS%%', '%%NAME%%' ],
              'delete':[ 'lun', 'destroy', '%%NAME%%' ],
              'map':[ 'lun', 'map', '-f', '%%NAME%%', '%%INITIATORGRP%%' ],
              'rebase':[ 'iscsi', 'connection', 'show' ],
              'snapshot':[ 'lun', 'clone', 'create', '%%SNAP_NAME%%', '-b', '%%NAME%%', '%%SNAP_PARENT%%'  ],
              'unmap':[ 'lun', 'unmap', '%%NAME%%', '%%INITIATORGRP%%' ]
              }

  # Most commands are expected to return nothing when they succeeded. The following
  # dictionnary lists exceptions and provides a pattern matching output in case of
  # success.
  # Keys must match an existing key in lun_cmds
  success_msg_pattern = { 'check':'online' 
                      }
  # Would be great to have it configurable as NetApp needs to know the client OS
  lunOS = 'linux'
  
  def __init__(self,proxy,mgtUser,mgtPrivKey,namespace,initiatorGroup,snapshotParent):
    self.proxyHost = proxy
    self.mgtUser = mgtUser
    self.mgtPrivKey = mgtPrivKey
    self.namespace = namespace
    self.initiatorGroup = initiatorGroup
    self.snapshotParent = snapshotParent

  # Return values:
  #    - the command corresponding to the action as a list of tokens, with iSCSI proxy related
  #      variables parsed.
  #    - the expected message pattern in case of success if the command output is not empty
  def getCmd(self,action):
    if action in self.lun_cmds.keys():
      command = self.lun_cmds[action]
      parsed_command = self.parse(command)
    else:
      abort("Internal error: action '%s' unknown" % (action))

    if action in self.success_msg_pattern:
      success_pattern = self.success_msg_pattern[action]
    else:
      success_pattern = None
      
    return parsed_command,success_pattern
    
  # Add command prefix and parse all variables related to iSCSI proxy in the command (passed as a list of tokens).
  # Return parsed command as a list of token.
  def parse(self,command):    
    # Build command to execute
    action_cmd = []
    action_cmd.extend(self.lun_cmd_prefix)
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
        action_cmd[i] = self.snapshotParent
      elif action_cmd[i] == '%%NAME%%':
        action_cmd[i] = self.namespace + "/%%UUID%%"
      elif action_cmd[i] == '%%SNAP_NAME%%':
        action_cmd[i] = self.namespace + "/%%SNAP_UUID%%"
    return action_cmd
    

class LUN:

  
  def __init__(self,uuid,size=None,proxy=None):
    self.uuid = uuid
    self.size = size
    self.proxy = proxy
    self.snapshotLUN = None
    
  def check(self):
    self.__executeCmd__('check')
    
  def create(self):
    self.__executeCmd__('create')
    
  def delete(self):
    self.__executeCmd__('delete')
    
  def map(self):
    self.__executeCmd__('map')
    
  def rebase(self,snapshot_lun):
    self.snapshotLUN = snapshot_lun
    self.__executeCmd__('rebase')
    
  def snapshot(self,snapshot_lun):
    self.snapshotLUN = snapshot_lun
    self.__executeCmd__('snapshot')
    
  def unmap(self):
    self.__executeCmd__('unmap')
    
  # Execute command on LUN.
  # In case an error occurs during one command, try to continue...
  # TODO: stop rather than continue if an error occurs? Also risky if there is no revert of action done...
  def __executeCmd__(self,action):
    cmd_toks,successMsg = self.proxy.getCmd(action)
    command = Command(action,self.parse(cmd_toks),successMsg)
    command.execute()
    command.checkStatus()

  # Parse all variables related to current LUN in the command (passed and returned as a list of tokens).  
  def parse(self,action_cmd):
    for i in range(len(action_cmd)):
      if action_cmd[i] == '%%SIZE%%':
        action_cmd[i] = "%sg" % self.size
      elif re.search('%%UUID%%',action_cmd[i]):
        action_cmd[i] = re.sub('%%UUID%%',self.uuid,action_cmd[i])
      elif re.search('%%SNAP_UUID%%',action_cmd[i]):
        action_cmd[i] = re.sub('%%SNAP_UUID%%',self.snapshotLUN.uuid,action_cmd[i])
    return action_cmd
    
    
class Command:
  
  def __init__(self,action,cmd,successMsg=None):
    self.action = action
    self.action_cmd = cmd
    self.successMsg = successMsg
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
    try:
      retcode = self.proc.wait()
      output = self.proc.communicate()[0]
      if retcode != 0:
          abort('Failed to execute %s action (error=%s). Command output:\n%s' % (self.action,retcode,output))
      else:
          # Need to check if the command is expected to return an output when successfull
          success = True
          if self.successMsg:
            if not re.search(self.successMsg,output):
              success = False
          else:
            if len(output) != 0:
              success = False
          if success:
            debug(1,'%s action completed successfully.' % (self.action))
          else:
            retcode = -1
            debug(0,'Failed to execute %s action. Command output:\n%s' % (self.action,output))
    except OSError, details:
      abort('Failed to execute %s action: %s' % (self.action,details))  
    return retcode


# Functions to handle logging

def abort(msg):
    logger.error("Persistent disk operation failed:\n%s" % (msg))
    sys.exit(2)

def debug(level,msg):
  if level <= verbosity:
    if level == 0:
      logger.info(msg)
    else:
      logger.debug(msg)


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
    action=rebase:   LUN_UUID New_LUN_UUID
    action=snapshot: LUN_UUID New_LUN_UUID Snapshot_Size
"""

parser = OptionParser(usage=usage_text)
parser.add_option('--config', dest='config_file', action='store', default=config_file_default, help='Name of the configuration file to use (D: %s)' % (config_file_default))
parser.add_option('--action', dest='action', action='store', default=action_default, help='Action to execute. Valid actions: %s'%(valid_actions_str))
parser.add_option('-v', '--debug', '--verbose', dest='verbosity', action='count', default=0, help='Increase verbosity level for debugging (on stderr)')
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
  lun_name_prefix=config.get(iscsi_proxy_name,'lun_namespace')
  initiator_group=config.get(iscsi_proxy_name,'initiator_group')
  snapshot_parent=config.get(iscsi_proxy_name,'snapshot_parent')
except:
  abort("Section %s missing or incomplete in configuration" % (iscsi_proxy_name))

try:
  mgt_user_name=config.get(iscsi_proxy_name,'mgt_user_name')  
except:
  try:
    mgt_user_name=config.get(config_main_section,'mgt_user_name')  
  except:
    abort("User name to use for connecting to iSCSI proxy undefined")
  
try:
  mgt_user_private_key=config.get(iscsi_proxy_name,'mgt_user_private_key')  
except:
  try:
    mgt_user_private_key=config.get(config_main_section,'mgt_user_private_key')  
  except:
    abort("SSH private key to use for connecting to iSCSI proxy undefined")


# Create iSCSI proxy object

iscsi_proxy = NetAppProxy(iscsi_proxy_name,mgt_user_name,mgt_user_private_key,lun_name_prefix,initiator_group,snapshot_parent)
    

# Execute requested action

if options.action == 'check':
  debug(1,"Checking LUN existence...")
  lun = LUN(args[0],proxy=iscsi_proxy)
  lun.check()
elif options.action == 'create':
  debug(1,"Creating LUN...")
  lun = LUN(args[0],size=args[1],proxy=iscsi_proxy)
  lun.create()
  lun.map()
elif options.action == 'delete':
  debug(1,"Deleting LUN...")
  lun = LUN(args[0],proxy=iscsi_proxy)
  lun.unmap()
  lun.delete()
elif options.action == 'rebase':
  debug(1,"Rebasing LUN...")
  lun = LUN(args[0],proxy=iscsi_proxy)
  snapshot_lun = LUN(args[1],proxy=iscsi_proxy)
  lun.rebase(snapshot_lun)
elif options.action == 'snapshot':
  debug(1,"Doing a LUN snapshot...")
  lun = LUN(args[1],proxy=iscsi_proxy)
  snapshot_lun = LUN(args[0],proxy=iscsi_proxy)
  lun.snapshot(snapshot_lun)
  snapshot_lun.map()
else:
  abort ("Internal error: unimplemented action (%s)" % (options.action))
  
