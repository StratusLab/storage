#!/usr/bin/env python
"""
Script to manage a iSCSI LUN on a NetApp filer
"""

__version__ = "0.0.1-1dev"
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
# Only keys matter, values are not used
valid_actions = { 'check':'', 'create':'', 'delete':'', 'rebase':'', 'snapshot':'' }
valid_actions_str = ', '.join(valid_actions.keys())

lun_cmd_prefix = [ 'ssh', '-x', '-i', '%%PRIVKEY%%','%%ISCSI_PROXY%%' ]
check_lun_cmd = [ 'lun', 'show', '%%NAME%%' ]
create_lun_cmd = [ 'lun', 'create', '-s', '%%SIZE%%', '-t', '%%LUNOS%%', '%%NAME%%' ]
delete_lun_cmd = [ 'lun', 'destroy', '%%NAME%%' ]
map_lun_cmd = [ 'lun', 'map', '-f', '%%NAME%%', '%%INITIATORGRP%%' ]
rebase_lun_cmd = [ 'iscsi', 'connection', 'show' ]
snapshot_lun_cmd = [ 'iscsi', 'connection', 'show' ]
unmap_lun_cmd = [ 'lun', 'unmap', '%%NAME%%', '%%INITIATORGRP%%' ]

# Would be great to have it configurable as NetApp needs to know the client OS
lun_os = 'linux'

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


def abort(msg):
    logger.error("Persistent disk creation failed:\n%s" % (msg))
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

parser = OptionParser(usage="usage: %prog [options] LUN_GUID LUN_Size")
parser.add_option('--config', dest='config_file', action='store', default=config_file_default, help='Name of the configuration file to use (D: %s)' % (config_file_default))
parser.add_option('--action', dest='action', action='store', default=action_default, help='Action to execute. Valid actions: %s'%(valid_actions_str))
parser.add_option('-v', '--debug', '--verbose', dest='verbosity', action='count', default=0, help='Increase verbosity level for debugging (on stderr)')
parser.add_option('--version', dest='version', action='store_true', default=False, help='Display various information about this script')
options, args = parser.parse_args()

if options.version:
  debug (0,"Version %s written by %s" % (__version__,__author__))
  debug (0,__doc__)
  sys.exit(0)

if len(args) < 2:
  debug(0,"Insufficient argument provided (2 required)")  
  parser.print_help()
  abort("")
  
if options.verbosity:
  verbosity = options.verbosity

lun_guid = args[0]
lun_size = args[1]


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
except ValueError:
  abort("Invalid value specified for 'iscsi_proxies' (section %s) (must be a comma-separated list)" % (config_main_section))

try:
  iscsi_proxy = iscsi_proxies[0]
  lun_name_prefix=config.get(iscsi_proxy,'lun_namespace')
  initiator_group=config.get(iscsi_proxy,'initiator_group')
except:
  abort("Section %s missing or incomplete in configuration" % (iscsi_proxy))

try:
  mgt_user_name=config.get(iscsi_proxy,'mgt_user_name')  
except:
  try:
    mgt_user_name=config.get(config_main_section,'mgt_user_name')  
  except:
    abort("User name to use for connecting to iSCSI proxy undefined")
  
try:
  mgt_user_private_key=config.get(iscsi_proxy,'mgt_user_private_key')  
except:
  try:
    mgt_user_private_key=config.get(config_main_section,'mgt_user_private_key')  
  except:
    abort("SSH private key to use for connecting to iSCSI proxy undefined")
  
if options.action == '':
  abort('No action specified (use --action to do it).')
elif not options.action in valid_actions:
  abort('Invalid action specified (%s). Valid actions are: %s.' % (options.action,valid_actions_str))
  
  
# Build command to execute based on action requested.
# This includes parsing special keyword in command string that have to be replaced by arguments

command_list = []
if options.action == 'check':
  debug(1,"Checking LUN existence...")
  command_list.append(check_lun_cmd)
elif options.action == 'create':
  debug(1,"Creating LUN...")
  command_list.append(create_lun_cmd)
  command_list.append(map_lun_cmd)
elif options.action == 'delete':
  debug(1,"Deleting LUN...")
  command_list.append(unmap_lun_cmd)
  command_list.append(delete_lun_cmd)
elif options.action == 'rebase':
  debug(1,"Rebasing LUN...")
  command_list.append(rebase_lun_cmd)
elif options.action == 'rebase':
  debug(1,"Doing a LUN snapshot...")
  command_list.append(snapshot_lun_cmd)
else:
  abort ("Internal error: unimplemented action (%s)" % (options.action))

# Execute all commands in command_list one by one
    
for command in command_list:
  # Build command to execute
  action_cmd = []
  action_cmd.extend(lun_cmd_prefix)
  action_cmd.extend(command)
  for i in range(len(action_cmd)):
    if action_cmd[i] == '%%SIZE%%':
      action_cmd[i] = "%sg" % lun_size
    elif action_cmd[i] == '%%INITIATORGRP%%':
      action_cmd[i] = initiator_group
    elif action_cmd[i] == '%%LUNOS%%':
      action_cmd[i] = lun_os
    elif action_cmd[i] == '%%NAME%%':
      action_cmd[i] = lun_name_prefix + '/' + lun_guid
    elif action_cmd[i] == '%%PRIVKEY%%':
      action_cmd[i] = mgt_user_private_key
    elif action_cmd[i] == '%%ISCSI_PROXY%%':
      action_cmd[i] = "%s@%s" % (mgt_user_name,iscsi_proxy)
  
  # Execute command: NetApp command don't return an exit code. When a command is sucessful,
  # its output is empty.
  debug(1,"Executing command: '%s'" % (' '.join(action_cmd)))
  try:
    proc = Popen(action_cmd, shell=False, stdout=PIPE, stderr=STDOUT)
    retcode = proc.wait()
    output = proc.communicate()[0]
    if retcode != 0:
        abort('Failed to execute %s action (error=%s). Command output:\n%s' % (options.action,retcode,output))
    else:
        if len(output) == 0:
          debug(1,'%s action completed successfully.' % (options.action))
        else:
          debug(0,'Failed to execute %s action. Command output:\n%s' % (options.action,output))
  except OSError, details:
    abort('Failed to execute %s action: %s' % (options.action,details))  
