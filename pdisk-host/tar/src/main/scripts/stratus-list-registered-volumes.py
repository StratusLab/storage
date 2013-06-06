#!/usr/bin/env python

#
# Copyright (c) 2013, Centre National de la Recherche Scientifique (CNRS)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import sys

sys.path.append('/var/lib/stratuslab/python')

import os
from optparse import OptionParser
import ConfigParser

CONFIG_FILE = '/etc/stratuslab/pdisk-host.conf'


class ListRegisteredVolumes(object):
    """
    Utility to list the persistent disk volume URIs registered with
    a virtual machine.
    """

    def __init__(self, args):

        self._read_configuration_file()
        self._process_arguments(args)

        self.registration_file = os.path.join(self.vm_dir, self.vm_id, self.register_filename)

    def _read_configuration_file(self):
        config = ConfigParser.ConfigParser()
        config.read(CONFIG_FILE)

        self.vm_dir = config.get('main', 'vm_dir')
        self.register_filename = config.get('main', 'register_filename')

    def _process_arguments(self, args):
        parser = OptionParser()

        parser.add_option("--vm-id", dest="vm_id",
                          help="VM ID", metavar="ID")

        parser.add_option("--vm-dir", dest="vm_dir",
                          help="directory where device will be created", metavar="DIR")

        options, _ = parser.parse_args(args)

        if not options.vm_id:
            raise parser.error('--vm-id option is mandatory')

        self.vm_id = options.vm_id

        if options.vm_dir:
            self.vm_dir = options.vm_dir

    def run(self):
        if os.path.exists(self.registration_file):
            with open(self.registration_file, 'r') as f:
                uris = f.read().splitlines()
        else:
            uris = []

        unique = []
        for uri in uris:
            uri = uri.strip()
            if uri and (not uri in unique):
                unique.append(uri)

        if unique:
            print "\n".join(unique)

if __name__ == "__main__":
    ListRegisteredVolumes(sys.argv).run()
