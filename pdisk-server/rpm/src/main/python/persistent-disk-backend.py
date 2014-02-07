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

import sys
import StringIO
from optparse import OptionParser
import logging.handlers
import ConfigParser

from stratuslab.pdiskbackend.LUN import LUN
from stratuslab.pdiskbackend.backends.CephBackend import CephBackend
from stratuslab.pdiskbackend.backends.FileBackend import FileBackend
from stratuslab.pdiskbackend.backends.LVMBackend import LVMBackend
from stratuslab.pdiskbackend.backends.NetAppBackend import getNetAppBackend, NETAPP_FLAVOURS

# Initializations
action_default = ''
status = 0           # Assume success

# Supported iSCSI proxy variants
iscsi_supported_variants = [ 'lvm', 'netapp' ]

# Keys are supported actions, values are the number of arguments required for the each action
valid_actions = { 'check':1, 'create':2, 'delete':1, 'rebase':1, 'snapshot':3 , 'getturl':1 , 'map':1 , 'unmap':1}
valid_actions_str = ', '.join(valid_actions.keys())

config_file_default = '/etc/stratuslab/pdisk-backend.cfg'
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

#[ceph.example.org]
#type=ceph
# Define default Ceph monitor endpoints. Use proxy host and default port if
# empty.
#monitors=host-mon1:6789,host-mon2:6789,
# Define the identity to authenticate the host/user.
#identity=cloud
# Define the Ceph pool where RBD images are stored.
#pool_name=cloud
# Define the base name for snapshots.
#snapshot_name=base
""")

#############
# Utilities #
#############

def read_configuration(config_defaults):
    """Read configuration file. The file must exists as there is no 
    sensible default value for several options.
    """
    config = ConfigParser.ConfigParser()
    config.readfp(config_defaults)
    try:
      config.readfp(open(options.config_file))
    except IOError, (errno,errmsg):
      if errno == 2:
        abort('Configuration file (%s) is missing.' % (options.config_file))
      else:
        abort('Error opening configuration file (%s): %s (errno=%s)' % (options.config_file,errmsg,errno))
    return config


#############
# Main code #
#############

# Parse configuration and options

usage_text = """usage: %prog [options] action_parameters

Parameters:
    action=check:    LUN_UUID
    action=create:   LUN_UUID LUN_Size
    action=delete:   LUN_UUID
    action=getturl:  LUN_UUID
    action=map:      LUN_UUID
    action=rebase:   LUN_UUID (will return the rebased LUN UUID on stdout)
    action=snapshot: LUN_UUID New_LUN_UUID Snapshot_Size
    action=unmap:    LUN_UUID
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

config = read_configuration(config_defaults)
  
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

if backend_variant.lower() in NETAPP_FLAVOURS:
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
  iscsi_proxy = getNetAppBackend(backend_variant, iscsi_proxies,
                                 backend_attributes['mgt_user_name'],
                                 backend_attributes['mgt_user_private_key'],
                                 backend_attributes['volume_name'],
                                 backend_attributes['lun_namespace'],
                                 backend_attributes['initiator_group'],
                                 backend_attributes['volume_snapshot_prefix'])

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

# Ceph back-end management
elif backend_variant.lower() == 'ceph':
  def get_mgt_info_from_config(config, main_section, backend_section):
    """ Return a tuple with the appropriated management information. """

    mgt_user_name = None
    mgt_user_private_key = None
    if backend_section != 'local':
      try:
        mgt_user_name = config.get(backend_section, 'mgt_user_name')
      except:
        try:
          mgt_user_name = config.get(main_section, 'mgt_user_name')
        except:
          raise ValueError('Undefined user name to connect to the proxy.')
      try:
        mgt_user_private_key = config.get(backend_section, 'mgt_user_private_key')
      except:
        try:
          mgt_user_private_key = config.get(main_section, 'mgt_user_private_key')
        except:
          raise ValueError('Undefined SSH private key to connect to the proxy.')
    return (mgt_user_name, mgt_user_private_key)

  backend_attributes = {'identity': None,
                        'monitors': None,
                        'pool_name': None,
                        'snapshot_name': None,
                       }
  try:
    for attribute in backend_attributes.keys():
      backend_attributes[attribute] = config.get(iscsi_proxy_name, attribute)
  except:
    abort("Section '%s' or required attribute '%s' missing" % (iscsi_proxy_name,attribute))

  try:
    mgt_user_name, mgt_user_private_key = get_mgt_info_from_config(config, config_main_section, iscsi_proxy_name)
  except ValueError as err:
    abort(err.message)

  monitors = None
  if backend_attributes['monitors']:
    monitors = backend_attributes['monitors'].split(',')

  iscsi_proxy = CephBackend(iscsi_proxy_name,
                            mgt_user_name,
                            mgt_user_private_key,
                            monitors=monitors,
                            identity=backend_attributes['identity'],
                            poolName=backend_attributes['pool_name'],
                            snapshotName=backend_attributes['snapshot_name'],
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
elif options.action == 'getturl' :
  debug(1,"Returning Transport URL...")
  lun = LUN(args[0], proxy=iscsi_proxy)
  turl = lun.getTurl()
  # If an error occured, it has already been signaled.
  # If it succeeds, rebasedLUN should always be defined...
  if turl:
    print turl
    status = 0
  else:
    status = 10
elif options.action == 'rebase':
  debug(1,"Rebasing LUN...")
  lun = LUN(args[0],proxy=iscsi_proxy)
  rebasedLUN = lun.rebase()
  # If an error occured, it has already been signaled.
  # If it succeeds, rebasedLUN should always be defined...
  if rebasedLUN:
    print rebasedLUN
    status = 0
  else:
    status = 10
elif options.action == 'snapshot':
  debug(1,"Doing a LUN snapshot...")
  lun = LUN(args[0],size=args[2],proxy=iscsi_proxy)
  snapshot_lun = LUN(args[1],proxy=iscsi_proxy)
  # Only the last error is returned
  status = lun.snapshot(snapshot_lun)
elif options.action == 'map':
  debug(1,"Mapping LUN...")
  lun = LUN(args[0],proxy=iscsi_proxy)
  status = lun.map()
elif options.action == 'unmap':
  debug(1,"Unmapping LUN...")
  lun = LUN(args[0],proxy=iscsi_proxy)
  status = lun.unmap()
else:
  abort ("Internal error: unimplemented action (%s)" % (options.action))
  
sys.exit(status)
