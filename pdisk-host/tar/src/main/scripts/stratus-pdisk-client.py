#!/usr/bin/env python

#
# Copyright (c) 2011, Centre National de la Recherche Scientifique (CNRS)
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

__version__ = "1.0"
__author__ = "Guillaume PHILIPPON <guillaume.philippon@lal.in2p3.fr>"

import sys

sys.path.append('/var/lib/stratuslab/python')

import os
import re
from optparse import OptionParser, OptionGroup
import ConfigParser
import socket
import httplib2
import json
from StringIO import StringIO
from urllib import urlencode
from subprocess import call
from time import sleep

sample_example = """
[main]
pdisk_user=pdisk
pdisk_passwd=xxxxxx
register_filename=pdisk
vm_dir=/var/lib/one
volume_mgmt_dir=/var/run/stratuslab

[iscsi]
iscsiadm=/usr/sbin/iscsiadm
"""
CONFFILE = '/etc/stratuslab/pdisk-host.conf'
config = ConfigParser.RawConfigParser()
config.read(CONFFILE)

iscsiadm = config.get("iscsi", "iscsiadm")
login = config.get("main", "pdisk_user")
pswd = config.get("main", "pdisk_passwd")
vm_dir = config.get("main", "vm_dir")
VOLUME_MGMT_DIR = config.get("main", "volume_mgmt_dir")

register_filename = config.get('main', 'register_filename')
if not register_filename:
    register_filename = 'pdisk'

parser = OptionParser()
parser.add_option("--pdisk-id", dest="persistent_disk_id",
                  help="persistent disk id ( pdisk:endpoint:port:disk_uuid )", metavar="PID")

parser.add_option("--vm-id", dest="vm_id",
                  help="VM ID", metavar="ID")

parser.add_option("--vm-dir", dest="vm_dir",
                  help="directory where device will be created", metavar="DIR")

parser.add_option("--vm-disk-name", dest="disk_name",
                  help="name of disk in Virtual Machine directory")

parser.add_option("--target", dest="target",
                  help="device name on Virtual Machine")

parser.add_option("--turl", dest="turl", metavar="TURL", default="",
                  help="transport URL of pdisk (protocol://server/protocol-option-to-access-file)")

parser.add_option("--username", dest="username",
                  help="username use to interact with pdisk server")

parser.add_option("--password", dest="password",
                  help="password use to interact with pdisk server")

action = OptionGroup(parser, " Action command")
action.add_option("--attach", dest="attach", action="store_true",
                  help="attach/detach backend to hypervisor")

action.add_option("--register", dest="registration", action="store_true",
                  help="register/unregister persistent disk as used on service")

action.add_option("--mark", dest="mark", action="store_true",
                  help="mark/unmark persistent disk as used in the VM")

action.add_option("--link", dest="link", action="store_true",
                  help="link/unlink attached disk in Virtual Machine directory")

action.add_option("--link-to", dest="link_to", default="",
                  help="link attached disk to the specified link name")

action.add_option("--mount", dest="mount", action="store_true",
                  help="mount/unmount disk into Virtual Machine")

action.add_option("--no-check", dest="no_check", action="store_true",
                  help="disable check if device is used")

action.add_option("--op", dest="operation", metavar="OP",
                  help="up : activate persistent disk ( register / attach / link / mount ) "
                       "-- down : deactivate persistent disk ( unmount / unlink / detach / unregister )")

parser.add_option_group(action)
(options, args) = parser.parse_args()

if not options.operation:
    raise parser.error("--op option is mandatory")

if not options.persistent_disk_id:
    raise parser.error("--pdisk-id option is mandatory")

if options.registration:
    if not options.vm_id:
        raise parser.error("--register option needs --vm-id options")

if options.link:
    if (not vm_dir and not options.vm_dir) or not options.vm_id or not options.disk_name:
        raise parser.error(
            "--link option needs --vm-disk-name, --vm-id options and --vm-dir if not defined in %s" % CONFFILE)

if options.mount:
    if not options.vm_id or not options.target:
        raise parser.error("--mount option needs --target and --vm-id options")

if options.mark:
    if (not vm_dir and not options.vm_dir) or not options.vm_id:
        raise parser.error(
            "--mark option needs --vm-id option and --vm-dir if not defined in %s" % CONFFILE)

if options.vm_dir:
    vm_dir = options.vm_dir

if options.username:
    login = options.username

if options.password:
    pswd = options.password

# Define the name of the file that lists the allocated persistent volumes.
registration_file = None
if options.mark:
    registration_file = os.path.join(vm_dir, options.vm_id, register_filename)


class VolumeManagement(object):
    """
    Class to manage local information about file
    """

    def __init__(self, directory):
        self.directory = directory

    def isFree(self, target):
        if len(os.listdir("%s/%s" % (self.directory, target))) == 0:
            return True
        else:
            return False

    def deleteTarget(self, target):
        f = '%s/%s/' % (self.directory, target)
        os.rmdir(f)

    def deleteVolume(self, target, turl):
        _turl = turl.replace('/', '-')
        f = '%s/%s/%s' % (self.directory, target, _turl)
        os.rmdir(f)

    def insertVolume(self, target, turl):
        _turl = turl.replace('/', '-')
        targetDir = "%s/%s" % (self.directory, target)
        f = "%s/%s" % (targetDir, _turl)
        if not os.path.isdir(self.directory):
            os.mkdir(self.directory)
        if not os.path.isdir(targetDir):
            os.mkdir(targetDir)
        os.mkdir(f)


class PersistentDisk:
    """Metaclass for general persistent disk client"""

    def __init__(self, pdisk_id, turl):
        try:
            __pdisk__ = re.match(r"pdisk:(?P<server>.*):(?P<port>.*):(?P<disk_uuid>.*)", pdisk_id)
            self.pdisk_uri = pdisk_id
            self.endpoint = __pdisk__.group('server')
            self.port = __pdisk__.group('port')
            self.disk_uuid = __pdisk__.group('disk_uuid')
            self.__checkTurl__(turl)
            self.volumeCheck = VolumeManagement(VOLUME_MGMT_DIR)
        except AttributeError:
            raise PersistentDiskException('URI ' + pdisk_id + ' not match expression pdisk:endpoint:port:disk_uuid')

    def __registration_uri__(self):
        return "https://" + self.endpoint + ":" + self.port + "/pswd/disks/" + self.disk_uuid + "/"

    def register(self, login, pswd, vm_id):
        """
          Register/Unregister mount on pdisk endpoint
        """
        node = socket.gethostbyname(socket.gethostname())
        url = self.__registration_uri__() + "mounts/"
        h = httplib2.Http()
        h.disable_ssl_certificate_validation = True
        h.add_credentials(login, pswd)
        data = dict(node=node, vm_id=vm_id, register_only="true")
        try:
            resp, contents = h.request(url, "POST", urlencode(data))
        except httplib2.ServerNotFoundError:
            msg = 'register: %s not found' % self.endpoint
            raise RegisterPersistentDiskException(msg)
        if resp.status >= 400:
            msg = 'register: POST on %s raised error %d' % (url, resp.status)
            raise RegisterPersistentDiskException(msg)

    def unregister(self, login, pswd, vm_id):
        node = socket.gethostbyname(socket.gethostname())
        url = self.__registration_uri__() + "mounts/" + self.disk_uuid + "_" + vm_id
        h = httplib2.Http()
        h.add_credentials(login, pswd)
        h.disable_ssl_certificate_validation = True
        try:
            resp, contents = h.request(url, "DELETE")
        except httplib2.ServerNotFoundError:
            msg = 'unregister: %s not found' % self.endpoint
            raise RegisterPersistentDiskException(msg)
        if resp.status >= 400:
            msg = 'unregister: DELETE on %s raised error %d' % (url, resp.status)
            raise RegisterPersistentDiskException(msg)

    def link(self, src, dst):
        """
          Link/Unlink, create a link between device ( image or physical device) and Virtual Machine directory
        """
        try:
            # If the link already exists, then remove it.
            # The lexists method MUST be used because the
            # exists method will return false for existing
            # but broken symbolic links!
            if os.path.lexists(dst):
                os.unlink(dst)
            os.symlink(src, dst)

            if not os.path.lexists(dst):
                msg = 'link: failed to create symlink %s' % dst
                raise LinkPersistentDiskException(msg)

            if not os.path.exists(dst):
                msg = 'link: created broken symlink from %s to %s' % (src, dst)
                raise LinkPersistentDiskException(msg)

        except Exception as e:
            msg = 'link: error while linking %s to %s; %s' % (src, dst, e)
            raise LinkPersistentDiskException(msg)

    def unlink(self, link):
        # See note above about lexists vs. exists.
        if os.path.lexists(link):
            os.unlink(link)

    def mount(self, vm_id, disk_name, target_device):
        """
          Mount/Unmount display device to a VM
        """

        hypervisor_device = vm_dir + "/" + str(vm_id) + "/images/" + disk_name

        domain_name = "one-" + str(vm_id)

        cmd = " ".join(['sudo', '/usr/bin/virsh', 'attach-disk',
                        domain_name, hypervisor_device, target_device])

        print >> sys.stderr, 'mount command: ' + cmd
        retcode = call(cmd, shell=True)
        if retcode != 0:
            msg = "mount: error mounting disk on hypervisor (%d)" % retcode
            raise MountPersistentDiskException(msg)

    def umount(self, vm_id, target_device):
        domain_name = "one-" + str(vm_id)

        cmd = " ".join(['sudo', '/usr/bin/virsh', 'detach-disk',
                        domain_name, target_device])

        print >> sys.stderr, 'unmount command: ' + cmd
        retcode = call(cmd, shell=True)
        if retcode != 0:
            msg = "unmount: error dismounting disk from hypervisor (%d)" % retcode
            raise MountPersistentDiskException(msg)

    def __copy__(self, pdisk):
        """
          creates a xxxPersistentDisk object from PersistentDisk (xxxPersistentDisk is a inherited class)
        """
        self.endpoint = pdisk.endpoint
        self.port = pdisk.port
        self.disk_uuid = pdisk.disk_uuid
        self.protocol = pdisk.protocol
        self.server = pdisk.server
        self.image = pdisk.image
        self.volumeCheck = pdisk.volumeCheck

    def check_mount(self, login, pswd):
        """
          check_mount used to check if pdisk is already used return true if pdisk is free
        """
        url = self.__registration_uri__() + "mounts/"
        h = httplib2.Http()
        h.add_credentials(login, pswd)
        h.disable_ssl_certificate_validation = True
        resp, contents = h.request(url)

        if resp.status != 200:
            msg = 'check_mount: GET on %s returned %d' % (url, resp.status)
            raise CheckPersistentDiskException(msg)

        io = StringIO(contents)
        json_output = json.load(io)

        if len(json_output) != 0:
            disk_id = 'pdisk:%s:%s:%s' % (self.endpoint, self.port, self.disk_uuid)
            msg = 'check_mount: %s is already mounted' % disk_id
            raise CheckPersistentDiskException(msg)

        return False

    def __checkTurl__(self, turl):
        """
        checks and splits the Transport URL ( proto://server:port/proto_options )
        from pdisk id ( pdisk:endpoint:port:disk_uuid )
        """
        if turl == "":
            __url__ = "iscsi://" + self.endpoint + ":3260/iqn.2011-01.eu.stratuslab:" + self.disk_uuid + ":1"
        else:
            __url__ = turl
        __uri__ = re.match(r"(?P<protocol>.*)://(?P<server>.*)/(?P<image>.*)", __url__)
        try:
            self.protocol = __uri__.group('protocol')
            self.server = __uri__.group('server')
            self.image = __uri__.group('image')
        except AttributeError:
            raise URIPersistentDiskException(
                'TURL ' + turl + ' not match expression protocol://server/protocol-options')

    def addToVolumeUriList(self):
        print >> sys.stderr, "Appending URI %s to %s..." % (self.pdisk_uri, registration_file)
        with open(registration_file, 'a') as f:
            f.write(self.pdisk_uri)
            f.write("\n")

    def removeFromVolumeUriList(self):
        if registration_file:
            print >> sys.stderr, "Removing URI %s from %s..." % (self.pdisk_uri, registration_file)
            with open(registration_file, 'r') as f:
                uris = f.splitlines()

            with open(registration_file, 'w') as f:
                for uri in uris:
                    if not (uri == self.pdisk_uri):
                        f.write(uri)
                        f.write("\n")


class IscsiPersistentDisk(PersistentDisk):
    _unix_device_path = '/dev/disk/by-path/'

    def __init__(self, pdisk_class, turl):
        self.__copy__(pdisk_class)
        self.__image2iqn__(pdisk_class.image)

    def _wait_until_appears(self):
        path = self.image_storage()
        iteration = 0
        while not os.path.exists(path):
            iteration += 1
            if iteration > 5:
                msg = "attach: storage path (%s) did not appear" % path
                raise AttachPersistentDiskException(msg)
            sleep(1)

    def _wait_until_disappears(self):
        path = self.image_storage()
        iteration = 0
        while os.path.exists(path):
            iteration += 1
            if iteration > 5:
                msg = "detach: storage path (%s) did not disappear" % path
                raise AttachPersistentDiskException(msg)
            sleep(1)

    def image_storage(self):
        __portal__ = re.match(r"(?P<server>.*):(?P<port>.*)", self.server)
        __portal_ip__ = socket.gethostbyname(__portal__.group('server'))

        dev = "ip-%s:%s-iscsi-%s-lun-%s" % (
            __portal_ip__, __portal__.group('port'), self.iqn, self.lun)

        return self._unix_device_path + dev

    def attach(self):
        __portal__ = re.match(r"(?P<server>.*):(?P<port>.*)", self.server)
        __portal_ip__ = socket.gethostbyname(__portal__.group('server'))

        reg = "sudo %s --mode node --portal %s:%s --target %s -o new" % (
            iscsiadm, __portal_ip__, __portal__.group('port'), self.iqn)
        retcode = call(reg, shell=True)
        if retcode != 0:
            msg = "attach: error registering iSCSI disk with hypervisor (%d)" % retcode
            raise AttachPersistentDiskException(msg)

        cmd = "sudo %s --mode node --portal %s:%s --target %s --login" % (
            iscsiadm, __portal_ip__, __portal__.group('port'), self.iqn)
        retcode = call(cmd, shell=True)
        if retcode == 0:
            filename = '%s-%s-%s' % (__portal_ip__, __portal__.group('port'), self.iqn)
            self.volumeCheck.insertVolume(filename, options.turl)
        if retcode != 0:
            if retcode == 15:
                cmd = "sudo %s --mode session --rescan" % (
                    iscsiadm)
                retcode = call(cmd, shell=True)
                if retcode == 0:
                    filename = '%s-%s-%s' % (__portal_ip__, __portal__.group('port'), self.iqn)
                    self.volumeCheck.insertVolume(filename, options.turl)
                else:
                    msg = "attach: error rescanning in iSCSI disk session (%d)" % retcode
                    raise AttachPersistentDiskException(msg)
            else:
                msg = "attach: error logging in iSCSI disk session (%d)" % retcode
                raise AttachPersistentDiskException(msg)

        # Seems to be a problem with the device by path being instantly available.
        self._wait_until_appears()

    def detach(self):
        portal = re.match(r"(?P<server>.*):(?P<port>.*)", self.server)
        portal_ip = socket.gethostbyname(portal.group('server'))
        hostname = socket.gethostname()

        filename = '%s-%s-%s' % (portal_ip, portal.group('port'), self.iqn)
        self.volumeCheck.deleteVolume(filename, options.turl)

        if self.volumeCheck.isFree(filename):
            cmd = 'sudo %s --mode node --portal %s:%s --target %s --logout' % (
                iscsiadm, portal_ip, portal.group('port'), self.iqn)
            retcode = call(cmd, shell=True)
            if retcode != 0:
                msg = 'detach: error detaching iSCSI disk from node %s (%s)' % (hostname, retcode)
                raise AttachPersistentDiskException(msg)

            sleep(2)

            unreg = "sudo %s --mode node --portal %s:%s --target %s -o delete" % (
                iscsiadm, portal_ip, portal.group('port'), self.iqn)
            retcode = call(unreg, shell=True)
            if retcode != 0:
                msg = 'detach: error unregistering iSCSI disk from node %s (%s)' % (hostname, retcode)
                raise AttachPersistentDiskException(msg)

            # Seems to be a problem with the device by path being instantly unavailable.
            self._wait_until_disappears()

            self.volumeCheck.deleteTarget(filename)

        # Give time for backend system to catch up.
        sleep(2)

    def __image2iqn__(self, s):
        __iqn__ = re.match(r"(?P<iqn>.*:.*):(?P<lun>.*)", s)
        self.iqn = __iqn__.group('iqn')
        self.lun = __iqn__.group('lun')


class FilePersistentDisk(PersistentDisk):

    def __init__(self, pdisk_class, turl):
        self.__copy__(pdisk_class)

    def __image2file__(self, s):
        __file__ = re.match(r"(?P<mount_point>.*)/(?P<full_path>.*)", s)
        self.mount_point = __file__.group('mount_point')
        self.full_path = __file__.group('full_path')

    def image_storage(self):
        return self.server + "/" + self.image

    def attach(self):
        pass

    def detach(self):
        pass


class PersistentDiskException(Exception):
    pass


class RegisterPersistentDiskException(PersistentDiskException):
    pass


class AttachPersistentDiskException(PersistentDiskException):
    pass


class URIPersistentDiskException(PersistentDiskException):
    pass


class LinkPersistentDiskException(PersistentDiskException):
    pass


class MountPersistentDiskException(PersistentDiskException):
    pass


class CheckPersistentDiskException(PersistentDiskException):
    pass


class getTurlPersistentDiskException(PersistentDiskException):
    pass


def do_up_operations(pdisk):
    try:
        if not options.no_check:
            print >> sys.stderr, "Checking for existing mount..."
            pdisk.check_mount(login, pswd)
        if options.registration:
            print >> sys.stderr, "Registering mount with pdisk service..."
            pdisk.register(login, pswd, options.vm_id)
        if options.attach:
            print >> sys.stderr, "Attaching disk to hypervisor..."
            pdisk.attach()
        if options.mark:
            print >> sys.stderr, "Marking pdisk in volume list file..."
            pdisk.addToVolumeUriList()
        if options.link_to:
            print >> sys.stderr, "Linking disk to %s" % options.link_to
            src = pdisk.image_storage()
            pdisk.link(src, options.link_to)
        if options.link:
            print >> sys.stderr, "Linking disk for virtual machine..."
            src = pdisk.image_storage()
            dst = vm_dir + "/" + str(options.vm_id) + "/images/" + options.disk_name
            pdisk.link(src, dst)
            print >> sys.stderr, "Created link: " + dst
        if options.mount:
            print >> sys.stderr, "Mounting disk on virtual machine..."
            pdisk.mount(options.vm_id, options.disk_name, options.target)
    except CheckPersistentDiskException as e:
        print >> sys.stderr, e
        exit(1)
    except RegisterPersistentDiskException as e:
        print >> sys.stderr, e
        exit(1)
    except AttachPersistentDiskException as e:
        print >> sys.stderr, e
        if options.registration:
            pdisk.unregister(login, pswd, options.vm_id)
        exit(1)
    except LinkPersistentDiskException as e:
        print >> sys.stderr, e
        if options.attach:
            pdisk.detach()
        if options.registration:
            pdisk.unregister(login, pswd, options.vm_id)
        exit(1)
    except MountPersistentDiskException as e:
        print >> sys.stderr, e

        if options.link:
            pdisk.unlink(dst)
        if options.attach:
            pdisk.detach()
        if options.registration:
            pdisk.unregister(login, pswd, options.vm_id)

        exit(1)


def do_down_operations(pdisk):
    try:
        if options.mount:
            print >> sys.stderr, "Unmount the disk from the virtual machine..."
            pdisk.umount(options.vm_id, options.target)
        if options.link:
            print >> sys.stderr, "Unlink the disk from the virtual machine..."
            dst = vm_dir + "/" + str(options.vm_id) + "/images/" + options.disk_name
            pdisk.unlink(dst)
        if options.attach:
            print >> sys.stderr, "Detach the disk from the hypervisor..."
            pdisk.detach()
        if options.mark:
            print >> sys.stderr, "Unmarking pdisk in volume list file..."
            pdisk.removeFromVolumeUriList()
        if options.registration:
            print >> sys.stderr, "Remove mount from the pdisk service..."
            pdisk.unregister(login, pswd, options.vm_id)
    except MountPersistentDiskException as e:
        print >> sys.stderr, e
        exit(1)
    except LinkPersistentDiskException as e:
        print >> sys.stderr, e
        exit(1)
    except AttachPersistentDiskException as e:
        print >> sys.stderr, e
        exit(1)
    except RegisterPersistentDiskException as e:
        print >> sys.stderr, e
        exit(1)


def __main__():
    try:
        global_pdisk = PersistentDisk(options.persistent_disk_id, options.turl)
    except getTurlPersistentDiskException:
        print >> sys.stderr, "Error while trying to retrieve %s" % options.persistent_disk_id
        return -1
    global vm_dir

    if global_pdisk.protocol == "iscsi":
        pdisk = IscsiPersistentDisk(global_pdisk, options.turl)
    elif global_pdisk.protocol == "file":
        pdisk = FilePersistentDisk(global_pdisk, options.turl)
    else:
        print >> sys.stderr, "Protocol " + global_pdisk.protocol + " not supported"
        exit(1)

    if options.operation == "up":
        do_up_operations(pdisk)

    elif options.operation == "down":
        do_down_operations(pdisk)

    else:
        raise parser.error("--op option only accepts 'up' or 'down'")


if __name__ == "__main__":
    __main__()
