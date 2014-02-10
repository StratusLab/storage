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
Script used by StratusLab PDisk to manage Backend LUNs.
"""

import sys
from optparse import OptionParser

from stratuslab.pdiskbackend.utils import Logger 
from stratuslab.pdiskbackend.LUN import LUN
from stratuslab.pdiskbackend.ConfigHolder import ConfigHolder
from stratuslab.pdiskbackend.PdiskBackendProxyFactory import PdiskBackendProxyFactory

# Initializations
action_default = ''
status = 0           # Assume success

# Keys are supported actions, values are the number of arguments required for the each action
valid_actions = { 'check':1, 'create':2, 'delete':1, 'rebase':1, 'snapshot':3 , 'getturl':1 , 'map':1 , 'unmap':1}
valid_actions_str = ', '.join(valid_actions.keys())

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
parser.add_option('--config', dest='config_file', action='store', default=config_file_default, 
                  help='Name of the configuration file to use (D: %s)' % (config_file_default))
parser.add_option('--action', dest='action', action='store', default=action_default, 
                  help='Action to execute. Valid actions: %s'%(valid_actions_str))
parser.add_option('-v', '--debug', '--verbose', dest='verbosity', action='count', 
                  default=0, help='Increase verbosity level for debugging (multiple allowed)')
options, args = parser.parse_args()


configHolder = ConfigHolder(config_file_name=options.config_file, 
                            verbosity=options.verbosity)
logger = Logger(configHolder)


if options.action in valid_actions:
    if len(args) < valid_actions[options.action]:
        logger.debug(0, "Insufficient argument provided (%d required)" % valid_actions[options.action])  
        parser.print_help()
        abort("")
else:
    if options.action:
        logger.debug(0,"Invalid action requested (%s)\n" % options.action)
    else:
        logger.debug(0,"No action specified\n")
    parser.print_help()
    abort("")

backend_proxy = PdiskBackendProxyFactory.createBackendProxy(configHolder)

# Execute requested action

if options.action == 'check':
    logger.debug(1,"Checking LUN existence...")
    lun = LUN(args[0], proxy=backend_proxy)
    status = lun.check()
elif options.action == 'create':
    logger.debug(1,"Creating LUN...")
    lun = LUN(args[0], size=args[1], proxy=backend_proxy)
    status = lun.create()
elif options.action == 'delete':
    logger.debug(1,"Deleting LUN...")
    lun = LUN(args[0], proxy=backend_proxy)
    status = lun.delete()
elif options.action == 'getturl' :
    logger.debug(1,"Returning Transport URL...")
    lun = LUN(args[0], proxy=backend_proxy)
    turl = lun.getTurl()
    # If an error occured, it has already been signaled.
    # If it succeeds, rebasedLUN should always be defined...
    if turl:
        print turl
        status = 0
    else:
        status = 10
elif options.action == 'rebase':
    logger.debug(1,"Rebasing LUN...")
    lun = LUN(args[0], proxy=backend_proxy)
    rebasedLUN = lun.rebase()
    # If an error occured, it has already been signaled.
    # If it succeeds, rebasedLUN should always be defined...
    if rebasedLUN:
        print rebasedLUN
        status = 0
    else:
        status = 10
elif options.action == 'snapshot':
    logger.debug(1,"Doing a LUN snapshot...")
    lun = LUN(args[0],size=args[2],proxy=backend_proxy)
    snapshot_lun = LUN(args[1],proxy=backend_proxy)
    # Only the last error is returned
    status = lun.snapshot(snapshot_lun)
elif options.action == 'map':
    logger.debug(1,"Mapping LUN...")
    lun = LUN(args[0],proxy=backend_proxy)
    status = lun.map()
elif options.action == 'unmap':
    debug(1,"Unmapping LUN...")
    lun = LUN(args[0],proxy=backend_proxy)
    status = lun.unmap()
else:
    abort("Internal error: unimplemented action (%s)" % options.action)

sys.exit(status)
