import re
import uuid

from .Command import Command

#################################################################
# Class describing a LUN and implementing the supported actions #
#################################################################

class LUN(object):

  # Some LUN commands (e.g. rebase) needs to return information as a string on stdout
  # that will be captured by pdisk. The return value is built mainly from information
  # capture in command output and returned as optInfo in __executeAction__ method.
  # In addition to these optional informations produced by the command, it is possible
  # to add a value built from LUN class attributes. This value s appended to other
  # optional values, if any.
  # In the following dictionnary, the key is an action for which this additional
  # information must be produced. And the value is a string defining the returned
  # value using the same tokens as commands.
  # This information is returned only on successful execution of action.
  # By default, a command returns nothing on stdout.
  additional_opt_info = {'rebase':'%%SNAP_UUID%%',
                        }
  
  def __init__(self,uuid,size=None,proxy=None):
    self.uuid = uuid
    self.size = size
    self.proxy = proxy
    # Another LUN involved in actions like rebase or snapshot
    self.associatedLUN = None
    
  def getUuid(self):
    return self.uuid
  
  def check(self):
    status,optInfo = self.__executeAction__('check')
    return status
    
  def create(self):
    status,optInfo = self.__executeAction__('create')
    return status
    
  def delete(self):
    status,optInfo = self.__executeAction__('delete')
    return status
    
  def getSize(self):
    status,self.size = self.__executeAction__('size')
    if status != 0:
      abort('Failure to retrieve size of LUN %s' % (self.uuid))
    return status

  def getTurl(self):
    status,self.turl = self.__executeAction__('getturl')
    if status != 0:
      abort('Failure to retrieve Transport URL of %s' % (self.uuid))
    return self.turl
    
  def map(self):
    status,optInfo = self.__executeAction__('map')
    return status
    
  def rebase(self):
    if self.proxy.newLunRequired('rebase'):
      #TODO: generate a UUID based on creation timestamp as in PDisk
      new_lun_uuid = str(uuid.uuid4())
      self.getSize()
      self.associatedLUN = LUN(new_lun_uuid,size=self.size,proxy=self.proxy)
      if self.associatedLUN.create() != 0:
        abort('An error occured creating a new LUN for rebasing %s' % (self.uuid))
    else:
      self.associatedLUN = self     # To simplify returned value
    status,rebasedLUN = self.__executeAction__('rebase')
    if status != 0:
      abort('Failure to rebase LUN %s' % (self.uuid))
    # Don't return directly self.associatedLUN but use optional information
    # returned by action execution to allow reformatting if needed.
    return rebasedLUN
    
  def snapshot(self,snapshot_lun):
    self.associatedLUN = snapshot_lun
    status,optInfo = self.__executeAction__('snapshot')
    return status
    
  def unmap(self):
    status,optInfo = self.__executeAction__('unmap')
    return status
    
    
  # Execute an action on a LUN.
  # An action may involve several actual commands : getCmd() method of proxy is a generator returning
  # the commands to execute one by one.
  # In case an error occurs during one command, try to continue...
  # Return the status of the last command executed and an optional additional value returned by the command.
  # Optionally a string is printed on stdout to allow the script to return information to the caller.
  # Special values for commands are:
  #  - None: action is not implemented
  #  - empty list: action does nothing  
  def __executeAction__(self,action):
    status = 0         # Assume success
    optInfos = None
    for cmd_toks,successMsg,failure_cmd_toks,backend_action in self.proxy.getCmd(action):
      optInfo = None
      # When returned command for action is None, it means that the action is not implemented
      if cmd_toks == None:
        abort("Action '%s' not implemented by back-end type '%s'" % (action,self.proxy.getType()))
      command = Command(backend_action,self.parseCmd(cmd_toks),successMsg)
      command.execute()
      status,optInfo = command.checkStatus()
      #print status,optInfo
      if status != 0 and failure_cmd_toks:
        command = Command(backend_action,self.parseCmd(failure_cmd_toks),successMsg)
        command.execute()
        status_,optInfo_ = command.checkStatus()        
        if status_ != 0:
          if not optInfo_:
            optInfo_ = ()
          print "Rollback command",failure_cmd_toks,"failed:",optInfo_
        break
      # If failure_cmd_toks is an amtpy string, stop LUN action processing
      elif failure_cmd_toks == '':
        break
      if optInfo:
        if optInfos:
          optInfos += optInfo
        else:
          optInfos = optInfo
    
    # Append an optional additional value built from LUN attributes, if necessary
    if status == 0 and action in self.additional_opt_info:
      if not optInfos:
        optInfos = ()
      optInfos += self.__parse__(self.additional_opt_info[action]),
      
    if optInfos:
      optInfosStr = self.proxy.formatOptInfos(action,optInfos)
    else:
      optInfosStr = None
    
    return status,optInfosStr

  # Parse all variables related to current LUN in the command (passed and returned as a list of tokens).  
  def parseCmd(self,action_cmd):
    for i in range(len(action_cmd)):
      action_cmd[i] = self.__parse__(action_cmd[i])
    return action_cmd
  
  # Do the actual string parsing
  def __parse__(self,string):
    if re.search('%%SIZE%%',string):
      string = re.sub('%%SIZE%%',self.size,string)
    elif re.search('%%SIZE_MB%%', string):
      string = re.sub('%%SIZE_MB%%', str(int(self.size) * 1024), string)
    elif re.search('%%UUID%%',string):
      string = re.sub('%%UUID%%',self.getUuid(),string)
    elif re.search('%%SNAP_UUID%%',string):
      string = re.sub('%%SNAP_UUID%%',self.associatedLUN.getUuid(),string)
    return string
    
