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

from stratuslab.pdiskbackend.LUN import LUN
from stratuslab.pdiskbackend import defaults
from stratuslab.pdiskbackend.utils import Logger
from stratuslab.pdiskbackend.ConfigHolder import ConfigHolder
from stratuslab.pdiskbackend.PdiskBackendProxyFactory import PdiskBackendProxyFactory

# Keys are supported actions, values are the number of arguments required for the each action
VALID_ACTIONS = {'check':1, 'create':2, 'delete':1, 'rebase':1, 
                 'snapshot':3, 'getturl':1 , 'map':1 , 'unmap':1}
VALID_ACTIONS_STR = ', '.join(VALID_ACTIONS.keys())

def parse_args():
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
    parser.add_option('--config', dest='config_file', action='store', 
                      default=defaults.CONFIG_FILE_NAME, 
                      help='Name of the configuration file to use (D: %s)' %\
                                                        defaults.CONFIG_FILE_NAME)
    parser.add_option('--action', dest='action', action='store', default='', 
                      help='Action to execute. Valid actions: %s' % VALID_ACTIONS_STR)
    parser.add_option('-v', '--debug', '--verbose', dest='verbosity',
                      action='count', default=defaults.VERBOSITY, 
                      help='Increase verbosity level for debugging (multiple allowed)')
    options, args = parser.parse_args()
    return options, args

options, args = parse_args()
ch = ConfigHolder(config_file_name=options.config_file, 
                  verbosity=options.verbosity)
logger = Logger(ch)


if options.action in VALID_ACTIONS:
    if len(args) < VALID_ACTIONS[options.action]:
        logger.debug(0, "Insufficient argument provided (%d required)" % VALID_ACTIONS[options.action])  
        parser.print_help()
        abort("")
else:
    if options.action:
        logger.debug(0,"Invalid action requested (%s)\n" % options.action)
    else:
        logger.debug(0,"No action specified\n")
    parser.print_help()
    abort("")

backend_proxy = PdiskBackendProxyFactory.createBackendProxy(ch)

# Execute requested action

status = 0

if options.action == 'check':
    logger.debug(1,"Checking LUN existence...")
    lun = LUN(args[0], proxy=backend_proxy, configHolder=ch)
    status = lun.check()
elif options.action == 'create':
    logger.debug(1,"Creating LUN...")
    lun = LUN(args[0], size=args[1], proxy=backend_proxy, configHolder=ch)
    status = lun.create()
elif options.action == 'delete':
    logger.debug(1,"Deleting LUN...")
    lun = LUN(args[0], proxy=backend_proxy, configHolder=ch)
    status = lun.delete()
elif options.action == 'getturl' :
    logger.debug(1,"Returning Transport URL...")
    lun = LUN(args[0], proxy=backend_proxy, configHolder=ch)
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
    lun = LUN(args[0], proxy=backend_proxy, configHolder=ch)
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
    lun = LUN(args[0],size=args[2],proxy=backend_proxy, configHolder=ch)
    snapshot_lun = LUN(args[1],proxy=backend_proxy, configHolder=ch)
    # Only the last error is returned
    status = lun.snapshot(snapshot_lun)
elif options.action == 'map':
    logger.debug(1,"Mapping LUN...")
    lun = LUN(args[0],proxy=backend_proxy, configHolder=ch)
    status = lun.map()
elif options.action == 'unmap':
    debug(1,"Unmapping LUN...")
    lun = LUN(args[0],proxy=backend_proxy, configHolder=ch)
    status = lun.unmap()
else:
    abort("Internal error: unimplemented action (%s)" % options.action)

sys.exit(status)
