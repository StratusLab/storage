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

sys.path.append('/var/lib/stratuslab/python')

from stratuslab.Util import printDetail
from stratuslab.pdiskbackend.LUN import LUN
from stratuslab.pdiskbackend import defaults
from stratuslab.pdiskbackend.utils import initialize_logger, abort, print_detail
from stratuslab.pdiskbackend.ConfigHolder import ConfigHolder
from stratuslab.pdiskbackend.PdiskBackendProxyFactory import PdiskBackendProxyFactory

# Keys are supported actions, values are the number of arguments required for the each action
VALID_ACTIONS = {'check':1, 'create':2, 'delete':1, 'rebase':1,
                 'snapshot':3, 'getturl':1 , 'map':1 , 'unmap':1}
VALID_ACTIONS_STR = ', '.join(VALID_ACTIONS.keys())

def parse_args(parser):
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
    parser.set_usage(usage_text)
    parser.add_option('--config', dest='config_file', action='store',
                      default=defaults.CONFIG_FILE_NAME,
                      help='Name of the configuration file to use (D: %s)' % \
                                                        defaults.CONFIG_FILE_NAME)
    parser.add_option('--action', dest='action', action='store', default='',
                      help='Action to execute. Valid actions: %s' % VALID_ACTIONS_STR)
    parser.add_option('--iscsi-proxy', dest='iscsi_proxy', action='store', default='',
                      help='ISCSI proxy to use. Same name section should exists in conifig.')
    parser.add_option('-v', '--debug', '--verbose', dest='verbosity',
                      action='count', default=defaults.VERBOSITY,
                      help='Increase verbosity level for debugging (multiple allowed)')
    options, args = parser.parse_args()
    return options, args

parser = OptionParser()
options, args = parse_args(parser)

ch = ConfigHolder(config_file_name=options.config_file,
                  verbosity=options.verbosity)
initialize_logger(ch.get(defaults.CONFIG_MAIN_SECTION, 'log_direction'),
                  ch.verbosity)

if options.action in VALID_ACTIONS:
    if len(args) < VALID_ACTIONS[options.action]:
        print_detail("Insufficient argument provided (%d required)" % VALID_ACTIONS[options.action])
        parser.print_help()
        abort("")
else:
    if options.action:
        print_detail("Invalid action requested (%s)\n" % options.action)
    else:
        print_detail("No action specified\n")
    parser.print_help()
    abort("")

backend_proxy = PdiskBackendProxyFactory.createBackendProxy(ch)

if options.iscsi_proxy:
    if not ch._config.has_section(options.iscsi_proxy):
        print_detail("Config should have same name section as the name "
                     "provided with --iscsi-proxy option.\n")
        parser.print_help()
        abort("")
    ch._config.set(CONFIG_MAIN_SECTION, 'iscsi_proxies', options.iscsi_proxy)

# Execute requested action

status = 0

if options.action == 'check':
    print_detail("Checking LUN existence...", 1)
    lun = LUN(args[0], proxy=backend_proxy)
    status = lun.check()
elif options.action == 'create':
    print_detail("Creating LUN...", 1)
    lun = LUN(args[0], size=args[1], proxy=backend_proxy)
    status = lun.create()
elif options.action == 'delete':
    print_detail("Deleting LUN...", 1)
    lun = LUN(args[0], proxy=backend_proxy)
    status = lun.delete()
elif options.action == 'getturl' :
    print_detail("Returning Transport URL...", 1)
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
    print_detail("Rebasing LUN...", 1)
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
    print_detail("Doing a LUN snapshot...", 1)
    lun = LUN(args[0], size=args[2], proxy=backend_proxy)
    snapshot_lun = LUN(args[1], proxy=backend_proxy)
    # Only the last error is returned
    status = lun.snapshot(snapshot_lun)
elif options.action == 'map':
    print_detail("Mapping LUN...", 1)
    lun = LUN(args[0], proxy=backend_proxy)
    status = lun.map()
elif options.action == 'unmap':
    print_detail("Unmapping LUN...", 1)
    lun = LUN(args[0], proxy=backend_proxy)
    status = lun.unmap()
else:
    abort("Internal error: unimplemented action (%s)" % options.action)

sys.exit(status)
