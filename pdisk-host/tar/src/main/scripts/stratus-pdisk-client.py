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

import os
import re
from optparse import OptionParser, OptionGroup
import ConfigParser
import socket
import json
import ssl
from StringIO import StringIO
from urllib import urlencode
from subprocess import call
from time import sleep, gmtime, strftime
import urlparse

import sys
sys.path.append('/var/lib/stratuslab/python')
import httplib2

sample_example = """
[main]
pdisk_user=pdisk
pdisk_passwd=xxxxxx
register_filename=pdisk
vm_dir=/var/lib/one
volume_mgmt_dir=/var/run/stratuslab

[iscsi]
iscsiadm=/usr/sbin/iscsiadm

[rbd]
binary=/usr/bin/rbd
devices=/dev/rbd
identity=cloud
"""
CONFFILE = '/etc/stratuslab/pdisk-host.conf'
config = ConfigParser.RawConfigParser()
config.read(CONFFILE)

login = config.get("main", "pdisk_user")
pswd = config.get("main", "pdisk_passwd")
vm_dir = config.get("main", "vm_dir")
VOLUME_MGMT_DIR = config.get("main", "volume_mgmt_dir")

# ISCSI configuration
iscsiadm = None
try:
    iscsiadm = config.get("iscsi", "iscsiadm")
except:
    pass

# Retrieve RBD configuration
rbd_bin = None
rbd_dev = None
rbd_id = None

try:
    rbd_bin = config.get('rbd', 'binary')
except:
    pass

try:
    rbd_dev = config.get('rbd', 'devices')
except:
    pass

try:
    rbd_id = config.get('rbd', 'identity')
except:
    pass

register_filename = config.get('main', 'register_filename')
if not register_filename:
    register_filename = 'pdisk'

parser = OptionParser()
parser.add_option("--pdisk-id", dest="persistent_disk_id",
                  help="persistent disk id ( pdisk://host:port/path/disk_uuid )", metavar="PID")

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
        if not os.path.isdir(f):
            os.mkdir(f)


class PersistentDisk:
    """Metaclass for general persistent disk client"""

    def __init__(self, pdisk_id, turl):
        try:
            url = urlparse.urlsplit(pdisk_id)
            path_elements = path.rstrip('/').split('/')
            uuid = path_elements.pop()
            endpoint_path = '/'.join(path_elements)
            self.pdisk_uri = pdisk_id
            self.endpoint = urlparse.unsplit(("https", url.netloc, url.endpoint_path, '', ''))
            self.port = (url.port or 443)
            self.hostname = url.hostname
            self.disk_uuid = uuid
            self.__checkTurl__(turl)
            self.volumeCheck = VolumeManagement(VOLUME_MGMT_DIR)
        except AttributeError:
            raise PersistentDiskException('URI ' + pdisk_id + ' not match pdisk://host:port/path/uuid')

    def __copy__(self, pdisk):
        """
          creates a xxxPersistentDisk object from PersistentDisk (xxxPersistentDisk is a inherited class)
        """
        self.pdisk_uri = pdisk.pdisk_uri
        self.endpoint = pdisk.endpoint
        self.port = pdisk.port
        self.hostname = pdisk.hostname
        self.disk_uuid = pdisk.disk_uuid
        self.protocol = pdisk.protocol
        self.server = pdisk.server
        self.image = pdisk.image
        self.volumeCheck = pdisk.volumeCheck

    def __registration_uri__(self):
        return "%s/pswd/disks/%s/" % (self.endpoint, self.disk_uuid)

    def register(self, login, pswd, vm_id):
        """
          Register/Unregister mount on pdisk endpoint
        """
        node = socket.gethostbyname(socket.gethostname())
        url = self.__registration_uri__() + "mounts/"
        encodedData = urlencode(dict(node=node, vm_id=vm_id, register_only="true"))
        try:
            resp, contents = self._httpRequestWithSSLErrorRetry(url, "POST", login, pswd, encodedData)
        except httplib2.ServerNotFoundError:
            msg = 'register: %s not found' % self.endpoint
            raise RegisterPersistentDiskException(msg)
        if resp.status >= 400:
            msg = 'register: POST on %s raised error %d' % (url, resp.status)
            raise RegisterPersistentDiskException(msg)

    def unregister(self, login, pswd, vm_id):
        node = socket.gethostbyname(socket.gethostname())
        url = self.__registration_uri__() + "mounts/" + self.disk_uuid + "_" + vm_id
        try:
            resp, contents = self._httpRequestWithSSLErrorRetry(url, "DELETE", login, pswd)
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

    def check_mount(self, login, pswd):
        """
          check_mount used to check if pdisk is already used return true if pdisk is free
        """
        url = self.__registration_uri__() + "mounts/"
        resp, contents = self._httpRequestWithSSLErrorRetry(url, "GET", login, pswd)

        if resp.status != 200:
            msg = 'check_mount: GET on %s returned %d' % (url, resp.status)
            raise CheckPersistentDiskException(msg)

        io = StringIO(contents)
        json_output = json.load(io)

        if len(json_output) != 0:
            msg = 'check_mount: volume already mounted ' % url
            raise CheckPersistentDiskException(msg)

        return False

    def __checkTurl__(self, turl):
        """
        checks and splits the Transport URL ( proto://server:port/proto_options )
        from pdisk id ( pdisk:endpoint:port:disk_uuid )
        """
        if turl == "":
            __url__ = "iscsi://" + self.hostname + ":3260/iqn.2011-01.eu.stratuslab:" + self.disk_uuid + ":1"
        else:
            __url__ = turl
        __uri__ = re.match(r"(?P<protocol>.*)://(?P<server>[^/]*)/(?P<image>.*)", __url__)
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
                uris = f.read().splitlines()

            with open(registration_file, 'w') as f:
                for uri in uris:
                    if not (uri == self.pdisk_uri):
                        f.write(uri)
                        f.write("\n")

    def _httpRequestWithSSLErrorRetry(self, url, method, login, pswd, encodedData=None):
        """
        Perform an HTTP operation, retrying if an SSLError is raised.
        Data should be urlencoded BEFORE passing it to this routine.
        """
        h = httplib2.Http()
        h.add_credentials(login, pswd)
        h.disable_ssl_certificate_validation = True

        maxRetries = 3
        retries = 0
        lastException = None
        while retries < maxRetries:

            try:
                if encodedData:
                    return h.request(url, method, encodedData)
                else:
                    return h.request(url, method)

            except ssl.SSLError as e:
                t = strftime("%Y-%m-%d %H:%M:%S", gmtime())
                print >> sys.stderr, 'SSL ERROR ENCOUNTERED (%s): %s' % (t, str(e))
                lastException = e
                retries += 1
            except httplib2.ssl_SSLError as e:
                t = strftime("%Y-%m-%d %H:%M:%S", gmtime())
                print >> sys.stderr, 'SSL ERROR ENCOUNTERED (%s): %s' % (t, str(e))
                lastException = e
                retries += 1

            sleep(2)

        raise lastException


class IscsiPersistentDisk(PersistentDisk):
    _unix_device_path = '/dev/disk/by-path/'

    ISCSI_SUCCESS = 0
    ISCSI_ERR_SESS_EXISTS = 15 # session is logged in
    ISCSI_ERR_NO_OBJS_FOUND = 21 # no records/targets/sessions/portals found to execute operation on

    detach_retcodes_ok = [ISCSI_SUCCESS, ISCSI_ERR_SESS_EXISTS, ISCSI_ERR_NO_OBJS_FOUND]

    @staticmethod
    def _rescan_sessions():
        return call("sudo %s --mode session --rescan" % (iscsiadm),
                    shell=True)

    def __init__(self, pdisk_class, turl):
        self.__copy__(pdisk_class)
        self._image2iqn(pdisk_class.image)
        _portal = re.match(r"(?P<server>.*):(?P<port>.*)", self.server)
        self._portal_host = _portal.group('server')
        self._portal_port = _portal.group('port')
        self._hostname = socket.gethostname()

    def _wait_lun_appears(self):
        path = self.image_storage()
        self._rescan_sessions()
        sleep(2)
        iteration = 0
        while not os.path.exists(path):
            iteration += 1
            if iteration >= 5:
                msg = "attach: storage path (%s) did not appear" % path
                raise AttachPersistentDiskException(msg)
            sleep(3)
            self._rescan_sessions()
            sleep(2)

    def _wait_lun_disappears(self):
        path = self.image_storage()
        self._rescan_sessions()
        iteration = 0
        while os.path.exists(path):
            iteration += 1
            if iteration > 5:
                msg = "detach: storage path (%s) did not disappear" % path
                raise AttachPersistentDiskException(msg)
            sleep(3)
            self._rescan_sessions()

    def image_storage(self):
        dev = "ip-%s:%s-iscsi-%s-lun-%s" % (
            self._portal_host, self._portal_port, self.iqn, self.lun)

        return self._unix_device_path + dev

    def attach(self):
        self._login_to_iscsi_target()
        # Seems to be a problem with the device by path being instantly available.
        self._wait_lun_appears()

    def _login_to_iscsi_target(self):
        self._register_new_target()
        self._login_to_target()

    def _register_new_target(self):
        self._iscsiadm('-o new',
                       'attach: error registering iSCSI disk with hypervisor %s' %
                                                                    self._hostname)

    def _login_to_target(self):
        cmd = "%s --login" % self._get_iscsiadm_cmd_base()
        retcode = call(cmd, shell=True)
        filename = '%s-%s-%s' % (self._portal_host, self._portal_port, self.iqn)
        if retcode == self.ISCSI_SUCCESS:
            self.volumeCheck.insertVolume(filename, options.turl)
        else:
            if retcode == self.ISCSI_ERR_SESS_EXISTS:
                retcode = self._rescan_sessions()
                if retcode == self.ISCSI_SUCCESS:
                    self.volumeCheck.insertVolume(filename, options.turl)
                else:
                    msg = "attach: error rescanning in iSCSI disk session on hypervisor %s (%d)" % \
                                                                            (self._hostname, retcode)
                    raise AttachPersistentDiskException(msg)
            else:
                msg = "attach: error logging in iSCSI disk session on hypervisor %s (%d)" % \
                                                                    (self._hostname, retcode)
                raise AttachPersistentDiskException(msg)

    def detach(self):
        filename = '%s-%s-%s' % (self._portal_host, self._portal_port, self.iqn)
        self.volumeCheck.deleteVolume(filename, options.turl)

        if self.volumeCheck.isFree(filename):
            self._logout_from_iscsi_target()
            # Seems to be a problem with the device by path being instantly unavailable.
            self._wait_lun_disappears()
            self.volumeCheck.deleteTarget(filename)

        # Give time for backend system to catch up.
        sleep(2)

    def _logout_from_iscsi_target(self):
        self._logout_from_target()
        sleep(2)
        self._delete_target_from_db()

    def _logout_from_target(self):
        self._iscsiadm('--logout',
                       'detach: error detaching iSCSI disk from hypervisor %s' % self._hostname,
                       retcodes_ok=self.detach_retcodes_ok)

    def _delete_target_from_db(self):
        self._iscsiadm('-o delete',
                       'detach: error unregistering iSCSI disk from hypervisor %s' % self._hostname,
                       retcodes_ok=self.detach_retcodes_ok)

    def _get_iscsiadm_cmd_base(self):
        return "sudo %s --mode node --portal %s:%s --target %s" % (
            iscsiadm, self._portal_host, self._portal_port, self.iqn)

    def _iscsiadm(self, op, ex_msg, retcodes_ok=[0]):
        """Parameters: operation, exception message, list of success return codes."""
        cmd = "%s %s" % (self._get_iscsiadm_cmd_base(), op)
        retcode = call(cmd, shell=True)
        if retcode not in retcodes_ok:
            raise AttachPersistentDiskException("%s (%d)" % (ex_msg, retcode))

    def _image2iqn(self, s):
        _iqn = re.match(r"(?P<iqn>.*:.*):(?P<lun>.*)", s)
        self.iqn = _iqn.group('iqn')
        self.lun = _iqn.group('lun')


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

class RBDPersistentDisk(PersistentDisk):
    """
    This class manages RBD images. It uses RBD kernel module to map/unmap
    images to the host and thus to the virtual machines.

    In order to work, the host must be configured to use a Ceph cluster (i.e.
    by setting the monitor endpoints) and to have the appropriated permissions
    to manage RBD images (i.e. by setting an identity).
    """

    def __init__(self, pdisk, binary='/usr/bin/rbd', devices='/dev/rbd', identity='cloud'):
        # Copy information from master class.
        self.__copy__(pdisk)

        # This variable defines the path to the RBD command.
        self.binary = binary

        # This variable defines the base path where the symlinks of mapped
        # images are created.
        self.devices = devices

        # This variable defines the identity to use to authenticate the host.
        # The associated keyring file must match
        # "ceph.client.<identity>.keyring" and must contain the secret.
        self.identity = identity

        # The <image> variable provided by the master class contains the image
        # fullname, i.e. <poolname>/<imagename>[@<snapshot-name>]. That's why
        # all components are extracted.
        self._extract_image_components()

        # The <server> variable provided by the master class contains the
        # "extended" server name of a Ceph monitor. To avoid this SPoF, it
        # should not be used directly but only in case of failure from the
        # other monitors indicated in the Ceph configuration.
        self._extract_server_components()

    def attach(self):
        """ Attach the RBD image to the host. """
        cmd = self._build_command('map', self.image)
        retcode = call(cmd, shell=True)
        if retcode != 0:
            msg = "attach: error mapping RBD image to the host (%d)" % retcode
            raise AttachPersistentDiskException(msg)

    def build_mapped_device(self):
        """
        Build the device path of the image. The returned path is a symlink to a
        real device.
        """
        return os.path.join(self.devices, self.image)

    def detach(self):
        """ Detach the RBD image from the host. """
        cmd = self._build_command('unmap', self.build_mapped_device())
        retcode = call(cmd, shell=True)
        if retcode != 0:
            msg = "detach: error unmapping RBD image from the host (%d)" % retcode
            raise AttachPersistentDiskException(msg)

    def image_storage(self):
        """ Return the device mapped to the image. """
        return self.build_mapped_device()

    def _build_command(self, operation, *parameters):
        """ Build a command from a RBD operation and its parameters. """
        command = 'sudo %s --id %s %s' % (self.binary, self.identity, operation)
        if parameters:
            command = '%s %s' % (command, ' '.join(parameters))
        return command

    def _extract_image_components(self):
        """
        Extract RBD image components (pool, image, snapshot) from the image
        fullname.
        """
        p = re.search(r'((?P<pool_name>.*)/)?(?P<image_name>)[^@]+(@(?P<snapshot_name>.*))?', self.image)
        self.pool_name = p.group('pool_name')
        self.image_name = p.group('image_name')
        self.snapshot_name = p.group('snapshot_name')

    def _extract_server_components(self):
        """
        Extract Ceph monitor components (endpoint, port) from the "extended"
        server name.
        """
        p = re.match(r'(?P<host>.*):(?P<port>.*)', self.server)
        self.monitor_host = p.group('host')
        self.monitor_port = p.group('port')


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
    elif global_pdisk.protocol == 'rbd':
        pdisk = RBDPersistentDisk(global_pdisk, rbd_bin, rbd_dev, rbd_id)
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
