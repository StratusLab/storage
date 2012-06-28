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

__version__ = "1.0"
__author__  = "Guillaume PHILIPPON <guillaume.philippon@lal.in2p3.fr>"


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
import commands

sample_example="""
[main]
pdisk_user=pdisk
pdisk_passwd=xxxxxx
register_filename=pdisk
getTurlCallback=/path/to/script
vm_dir=/var/lib/one

[iscsi]
iscsiadm=/usr/sbin/iscsiadm
"""
config = ConfigParser.RawConfigParser()
config.read('/etc/stratuslab/pdisk-host.conf')

iscsiadm=config.get("iscsi","iscsiadm")
login=config.get("main","pdisk_user")
pswd=config.get("main","pdisk_passwd")
getTurlCallback=config.get("main","getTurlCallback")
vm_dir=config.get("main","vm_dir")

parser=OptionParser()
parser.add_option("--pdisk-id", dest="persistent_disk_id",
  help="persistent disk id ( pdisk:endpoint:port:disk_uuid )", metavar="PID"
)
parser.add_option("--vm-id", dest="vm_id",
  help="VM ID", metavar="ID"
)
parser.add_option("--vm-dir", dest="vm_dir",
  help="directory where device will be created", metavar="DIR"
)
parser.add_option("--vm-disk-name", dest="disk_name",
  help="name of disk in Virtual Machine directory"
)
parser.add_option("--target", dest="target",
  help="device name on Virtual Machine"
)
parser.add_option("--turl", dest="turl", metavar="TURL", default="",
  help="transport URL of pdisk (protocol://server/protocol-option-to-access-file)"
)
parser.add_option("--username", dest="username",
  help="username use to interact with pdisk server"
)
parser.add_option("--password", dest="password",
  help="password use to interact with pdisk server"
)

action=OptionGroup(parser," Action command")
action.add_option("--attach", dest="attach", action="store_true",
  help="attach/detach backend to hypervisor"
)
action.add_option("--register", dest="registration", action="store_true",
  help="register/unregister persistent disk as used on service"
)
action.add_option("--link", dest="link", action="store_true",
  help="link/unlink attached disk in Virtual Machine directory"
)
action.add_option("--mount", dest="mount", action="store_true",
  help="mount/unmount disk into Virtual Machine"
)
action.add_option("--no-check", dest="no_check", action="store_true",
  help="disable check if device is used"
)
action.add_option("--op", dest="operation", metavar="OP",
  help="up : activate persistent disk ( register / attach / link / mount ) -- down : deactivate persistent disk ( unmount / unlink / detach / unregister )"
)
parser.add_option_group(action)
(options, args) = parser.parse_args()

if not options.operation:
	raise parser.error("--op option is mandatory")

if options.attach:
	if not options.persistent_disk_id :
		raise parser.error("--attach option needs --pdisk-id option")

if options.registration:
	if not options.persistent_disk_id or not options.vm_id:
		raise parser.error("--register option needs --pdisk-id and --vm-id options")

if options.link:
	if not options.persistent_disk_id or  ( not vm_dir and not options.vm_dir ) or not options.vm_id or not options.disk_name :
		raise parser.error("--link option needs --pdisk-id, --vm-disk-name, --vm-id options, --vm-dir is not defined in configuration file (/etc/stratuslab/pdisk-host.conf)")

if options.mount:
	if not options.persistent_disk_id or not options.vm_id or not options.target:
		raise parser.error("--mount option needs --pdisk-id, --target and --vm-id options")

if not options.persistent_disk_id:
	raise parser.error("--pdisk-id option is mandatory")

if options.vm_dir:
	vm_dir = options.vm_dir

if options.username:
	login = options.username

if options.password:
	pswd = options.password

"""
 Metaclass for general persistent disk client
"""
class PersistentDisk:
	def __registration_uri__(self):
		return "https://"+self.endpoint+":"+self.port+"/pswd/disks/"+self.disk_uuid+"/"

	"""
		Register/Unregister mount on pdisk endpoint
	"""
	def register(self,login,pswd,vm_id):
		node=socket.gethostbyname(socket.gethostname())
		url = self.__registration_uri__()+"mounts/"
		h = httplib2.Http("/tmp/.cache")
		h.disable_ssl_certificate_validation=True
		h.add_credentials(login,pswd)
		data = dict(node=node, vm_id=vm_id,register_only="true")
		try:
			resp, contents = h.request(url,"POST",urlencode(data))
		except httplib2.ServerNotFoundError:
                        msg = 'register: %s not found' % self.endpoint
			raise RegisterPersistentDiskException(msg)
                if (resp.status >= 400):
                        msg = 'register: POST on %s raised error %d' % (url, resp.status)
                        raise RegisterPersistentDiskException(msg)

	def unregister(self,login,pswd,vm_id):
		node=socket.gethostbyname(socket.gethostname())
		url = self.__registration_uri__()+"mounts/"+self.disk_uuid+"_"+vm_id
		h = httplib2.Http("/tmp/.cache")
		h.add_credentials(login,pswd)
		h.disable_ssl_certificate_validation=True
		try:
			resp, contents = h.request(url,"DELETE")
		except httplib2.ServerNotFoundError:
                        msg = 'unregister: %s not found' % self.endpoint
			raise RegisterPersistentDiskException(msg)
                if (resp.status >= 400):
                        msg = 'unregister: DELETE on %s raised error %d' % (url, resp.status)
                        raise RegisterPersistentDiskException(msg)

	"""
		Link/Unlink, create a link between device ( image or physical device) and Virtual Machine directory
	"""
	def link(self,src,dst):
		try:
			if os.path.exists(dst):
				os.unlink(dst)
			os.symlink(src,dst)
		except:
                        msg = 'link: error while linking %s to %s' % (src, dst)
			raise LinkPersistentDiskException(msg)

	def unlink(self,link):
		if os.path.exists(link):
			os.unlink(link)

	"""
		Mount/Unmount display device to a VM
	"""
	def mount(self,vm_id,disk_name,target_device):
		hypervisor_device=vm_dir+"/"+str(vm_id)+"/images/"+disk_name
		domain_name="one-"+str(vm_id)
		cmd="sudo /usr/bin/virsh attach-disk "+domain_name+" "+hypervisor_device+" "+target_device
		retcode=call(cmd,shell=True)

	def umount(self,vm_id,target_device):
		domain_name="one-"+str(vm_id)
		cmd="sudo /usr/bin/virsh detach-disk "+domain_name+" "+target_device
		retcode=call(cmd,shell=True)
	"""
		__copy__ used to create a a xxxPersistentDisk object from PersistentDisk (xxxPersistentDisk is a inherited class)
	"""
	def __copy__(self,pdisk):
		self.endpoint  = pdisk.endpoint
		self.port      = pdisk.port
		self.disk_uuid = pdisk.disk_uuid
		self.protocol  = pdisk.protocol
                self.server    = pdisk.server
                self.image     = pdisk.image

	"""
		check_mount used to check if pdisk is already used return true if pdisk is free
	"""
	def check_mount(self,login,pswd):
		url = self.__registration_uri__()
		h = httplib2.Http("/tmp/.cache")
		h.add_credentials(login,pswd)
		h.disable_ssl_certificate_validation=True
		resp, contents = h.request(url)

		if resp.status != 200:
                        msg = 'check_mount: GET on %s returned %d' % (url, resp.status)
			raise CheckPersistentDiskException(msg)

		io = StringIO(contents)
		json_output = json.load(io)

		if json_output['count'] != '0':
                        disk_id = 'pdisk:%s:%s:%s' % (self.endpoint, self.port, self.disk_uuid)
                        msg = 'check_mount: %s is already mounted' % disk_id
			raise CheckPersistentDiskException(msg)

		return False

	"""
		__checkTurl__ check and split Transport URL ( proto://server:port/proto_options ) from pdisk id ( pdisk:endpoint:port:disk_uuid )
	"""
	def __checkTurl__(self,turl):
		if turl == "":
			__url__ = "iscsi://"+self.endpoint+":3260/iqn.2011-01.eu.stratuslab:"+self.disk_uuid+":1"
		else:
			__url__ = turl
		__uri__ = re.match(r"(?P<protocol>.*)://(?P<server>.*)/(?P<image>.*)", __url__)
		try :
			self.protocol  = __uri__.group('protocol')
			self.server    = __uri__.group('server')
			self.image     = __uri__.group('image')
		except AttributeError:
			raise URIPersistentDiskException('TURL '+ turl + ' not match expression protocol://server/protocol-options')

	def __init__(self, pdisk_id, turl):
		try:
			__pdisk__ = re.match(r"pdisk:(?P<server>.*):(?P<port>.*):(?P<disk_uuid>.*)", pdisk_id)
			self.endpoint  = __pdisk__.group('server')
			self.port      = __pdisk__.group('port')
			self.disk_uuid = __pdisk__.group('disk_uuid')
			self.__checkTurl__(turl)
		except AttributeError:
			raise PersistentDiskException('URI '+pdisk_id+' not match expression pdisk:endpoint:port:disk_uuid')
			

class IscsiPersistentDisk(PersistentDisk):

	_unix_device_path='/dev/disk/by-path/'

	def image_storage(self):
		__portal__    = re.match(r"(?P<server>.*):(?P<port>.*)", self.server)
		__portal_ip__ = socket.gethostbyname( __portal__.group('server') )

		dev = "ip-%s:%s-iscsi-%s-lun-%s" % (
                        __portal_ip__,  __portal__.group('port'), self.iqn, self.lun)

		return self._unix_device_path+dev

	def attach(self):
		__portal__    = re.match(r"(?P<server>.*):(?P<port>.*)", self.server)
		__portal_ip__ = socket.gethostbyname(__portal__.group('server'))

		reg = "sudo %s --mode node --portal %s:%s --target %s -o new" % (
                        iscsiadm, __portal_ip__,  __portal__.group('port'), self.iqn) 
		retcode = call(reg, shell=True)
		if retcode < 0:
			msg = "attach: error registering iSCSI disk with hypervisor (%d)" % retcode
			raise AttachPersistentDiskException(msg)

		cmd = "sudo %s --mode node --portal %s:%s --target %s --login" % (
                        iscsiadm, __portal_ip__,  __portal__.group('port'), self.iqn) 
		retcode = call(cmd, shell=True)
		if retcode < 0:
			msg = "attach: error logging in iSCSI disk session (%d)" % retcode
			raise AttachPersistentDiskException(msg)

	def detach(self):
		__portal__    = re.match(r"(?P<server>.*):(?P<port>.*)", self.server)
		__portal_ip__ = socket.gethostbyname(__portal__.group('server'))

		cmd = 'sudo %s --mode node --portal %s:%s --target %s --logout' % (
                        iscsiadm,  __portal_ip__,  __portal__.group('port'), self.iqn)
		retcode = call(cmd, shell=True)
		if retcode < 0:
                        msg = 'detach: error detaching iSCSI disk from hypervisor (%d)' % retcode
			raise AttachPersistentDiskException(msg)

		unreg = "sudo %s --mode node --portal %s:%s --target %s -o delete" % (
                        iscsiadm, __portal_ip__,  __portal__.group('port'), self.iqn) 
		retcode = call(unreg, shell=True)
		if retcode < 0:
                        msg = 'detach: error unregistering iSCSI disk with hypervisor (%s)' % retcode
			raise AttachPersistentDiskException(msg)

	def __image2iqn__(self, str):
		__iqn__ = re.match(r"(?P<iqn>.*:.*):(?P<lun>.*)", str)
		self.iqn = __iqn__.group('iqn')
		self.lun = __iqn__.group('lun')

	def __init__(self,pdisk_class,turl):
		self.__copy__(pdisk_class)
		self.__image2iqn__(pdisk_class.image)

class FilePersistentDisk(PersistentDisk):
	def __image2file__(self, str):
		__file__ = re.match(r"(?P<mount_point>.*)/(?P<full_path>.*)", str)
		self.mount_point = __file__.group('mount_point')
		self.full_path   = __file__.group('full_path')
	def image_storage(self):
		return self.server+"/"+self.image
	def attach(self):
		pass

	def detach(self):
		pass

	def __init__(self,pdisk_class,turl):
		self.__copy__(pdisk_class)

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
                        pdisk.check_mount(login,pswd)
                if options.registration:
                        pdisk.register(login,pswd,options.vm_id)
                if options.attach:
                        pdisk.attach()
                if options.link:
                        src = pdisk.image_storage()
                        dst = vm_dir+"/"+str(options.vm_id)+"/images/"+options.disk_name
                        pdisk.link(src,dst)
                if options.mount:
                        pdisk.mount(options.vm_id,options.disk_name,options.target)
        except CheckPersistentDiskException as e:
                print e
                exit(1)
        except RegisterPersistentDiskException as e:
                print e
                exit(1)
        except AttachPersistentDiskException as e:
                print e
                if options.registration:
                        pdisk.unregister(login,pswd,options.vm_id)
                exit(1)
        except LinkPersistentDiskException:
                print e
                if options.attach:
                        pdisk.detach()
                if options.registration:
                        pdisk.unregister(login,pswd,options.vm_id)
        except MountPersistentDiskException:
                print "Error while try to mount %s to %s" % ( options.persistent_disk_id , options.vm_id )
                if options.link:
                        pdisk.unlink(dst)
                if options.attach:
                        pdisk.detach()
                if options.registration:
                        pdisk.unregister(login,pswd,options.vm_id)

def do_down_operations(pdisk):
        try:
                if options.mount:
                        pdisk.umount(options.vm_id,options.target)
                if options.link:
                        dst = vm_dir+"/"+str(options.vm_id)+"/images/"+options.disk_name
                        pdisk.unlink(dst)
                if options.attach:
                        pdisk.detach()
                if options.registration:
                        pdisk.unregister(login,pswd,options.vm_id)
        except MountPersistentDiskException:
                print "Error while try to umount %s to %s " % ( options.persistent_disk_id , options.vm_id )
        except LinkPersistentDiskException:
                print "Error while try to unlink %s" % dst
        except AttachPersistentDiskException as e:
                print e
                exit(1)
        except RegisterPersistentDiskException:
                print e
                exit(1)

def __init__():
	try:
		global_pdisk = PersistentDisk(options.persistent_disk_id,options.turl)
	except getTurlPersistentDiskException:
		print "Error while trying to retrieve %s" % options.persistent_disk_id
		return -1
	global vm_dir

	if global_pdisk.protocol == "iscsi" :
		pdisk = IscsiPersistentDisk(global_pdisk,options.turl)
	elif global_pdisk.protocol == "file" :
		pdisk = FilePersistentDisk(global_pdisk,options.turl)
	else :
		print "Protocol "+global_pdisk.protocol+" not supported"
                exit(1)

	if options.operation == "up":
                do_up_operations(pdisk)

	elif options.operation == "down":
                do_down_operations(pdisk)
		
	else:
		raise parser.error("--op option only accepts 'up' or 'down'")
__init__()
