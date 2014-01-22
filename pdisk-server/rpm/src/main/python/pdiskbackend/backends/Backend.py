import re
import socket

from pdiskbackend.utils import abort
from pdiskbackend.backends.BackendCommand import BackendCommand

####################################################################
# Superclass describing common aspect of every iSCSI backends      #
# Inherited by all iSCSI backend classes.                          #
# Variable required in all backend implementationss are documented #
# here but generally have empty defaults.                          #
####################################################################

class Backend(object):
  # Command prefix to use to connect through ssh
  ssh_cmd_prefix = [ 'ssh', '-x', '-i', '%%PRIVKEY%%','%%ISCSI_PROXY%%' ]

  cmd_prefix = []

  # Table defining mapping of LUN actions to NetApp actions.
  # This is a 1 to n mapping (several NetApp commands may be needed for one LUN action).
  # map and unmap are necessary as separate actions as they are not necessarily executed on
  # the same LUN as the other operations (eg. snapshot action).
  # Special values for commands are:
  #  - None: action is not implemented
  #  - empty list: action does nothing
  lun_backend_cmd_mapping = {'check':None,
                             'create':None,
                             'delete':None,
                             'getturl':None,
                             'map':None,
                             'rebase':None,
                             'size':None,
                             'snapshot':None,
                             'unmap':None,
                            }
  
  # Definitions of NetApp commands used to implement actions.
  backend_cmds = {
                  }

  # Commands to execute when a given backend command fails.
  # This is a dictionnary where key is one of the backend_cmds key and the value is another dictionnary
  # whose key is the name of lun action (as defined in lun_backend_cmd_mapping) that defines the
  # context of the backend command and the value is the key of another command in backend_cmds).
  # IF the context is '-', the alternate command will be executed in any context (lun actions) in case of errors.
  # If the value (alternate command) is an empty string, further backend commands part of the same LUN action are skipped.
  # If it is None,processing of further actions contnues as if there was no entry for the command in the dictionnary.
  # IF a backup command fails and has no entry in this dictionary, the execution continues
  # with next command if any.
  backend_failure_cmds = {
                          }

  # Most commands are expected to return nothing when they succeeded. The following
  # dictionary lists exceptions and provides a pattern matching output in case of
  # success.
  # Keys must match an existing key in backend_cmds
  success_msg_pattern = {
                        }
  
  # Some commands may fail (exit with return code greater than zero), but depending 
  # on the operation and by examining its output we might want to decide to mark the 
  # operation as successful. The following dictionary lists exceptions and provides 
  # a pattern matching output in case of failure.
  # Keys must match an existing key in backend_cmds
  failure_ok_msg_pattern = {
                            }

  # Some backend actions must return a value (eg. LUN, TURL...)
  # that is build from the output of executed commands. This dictionary
  # allows to specify how the final value returned is built from the
  # command outputs (each action may execute several commands, each one
  # returning several values).
  # Keys must match an existing key in lun_backend_cmd_mapping.
  # Value must be a valid formatting instruction.
  opt_info_format = {
                    }
  
  # The creation of a new LUN may be required by some operations
  # on some backends (e.g. rebase with LVM backend).
  # This dictionnary allows to define which LUN actions (same keys
  # as in lun_backend_cmd_mapping, value ignored).
  # By default, this variable is empty: redefine it appropriately in
  # the context of a particular backend if needed.
  new_lun_required = {
                      }

  # Generator function returning:
  #    - the command corresponding to the action as a list of tokens, with iSCSI proxy related
  #      variables parsed.
  #    - the expected message patterns in case of success if the command output is not empty. This is returned as
  #      a list of patterns (a simple string is converted to a list).
  # This function must be called from an iteration loop control statement
  def getCmd(self,lun_action):
      if lun_action in self.lun_backend_cmd_mapping:
          backend_actions = self.lun_backend_cmd_mapping[lun_action]
      else:
          abort("Internal error: LUN action '%s' unknown" % (lun_action))
      
      if backend_actions == None:
          yield None
            
      for action in backend_actions:
          yield BackendCommand(command=self._get_parsed_command(action), 
                               success_patterns=self._get_success_patterns(action), 
                               failure_command=self._get_failure_command(action),
                               failure_ok_patterns=self._get_failure_ok_patterns(action), 
                               action=action)
    
  def _get_parsed_command(self, action):
      if action in self.backend_cmds.keys():
          return self._buildCmd(self.backend_cmds[action])
      else:
          abort("Internal error: action '%s' unknown" % (action))

  def _get_success_patterns(self, action):
      success_patterns = None
      if action in self.success_msg_pattern:
          success_patterns = self.success_msg_pattern[action]
          if isinstance(success_patterns,str):
              success_patterns = [ success_patterns ]
      return success_patterns

  def _get_failure_command(self, action):
      failure_command = None
      if action in self.backend_failure_cmds:
          failure_actions = self.backend_failure_cmds[action]
          if lun_action in failure_actions:
              command = failure_actions[lun_action]
          # '-' is a special key value meaning the alternate command applies to all LN actions
          elif '-' in failure_actions:
              command = failure_actions['-']
          else:
              command = None
          if command:  
              failure_command = self._buildCmd(command)

      return failure_command

  def _get_failure_ok_patterns(self, action):
      failure_ok_patterns = None
      if action in self.failure_ok_msg_pattern:
          failure_ok_patterns = self.failure_ok_msg_pattern[action]
          if isinstance(failure_ok_patterns,str):
              failure_ok_patterns = [ failure_ok_patterns ]
      return failure_ok_patterns

  # Method returning true if creation of a new LUN is required for a particular LUN action.
  # LUN creation is the responsibility of the caller.
  def newLunRequired(self,action):
    return action in self.new_lun_required
     
  # Method formatting optional information returned by executed commands as a string.
  # Optional information are passed as a tuple.
  # Formatting instructions are retrieved in a backend-specific dictionnary with one entry
  # per action requiring a specific formatting of optional informations. The value is
  # a standard formatting instruction that may contain %%KWORD%% that are substituted with
  # appropriate value.
  # If there is no specific instructions for a given action, just join all list elements as a space
  # separated string.
  def formatOptInfos(self,action,optInfos):
    if not optInfos:
      return
    if action in self.opt_info_format:
      optInfosFmt = self._detokenize(self.opt_info_format[action])
      return optInfosFmt % optInfos
    else:
      return ' '.join(optInfos)

  # Add command prefix and parse all variables related to iSCSI proxy in the command (passed as a list of tokens).
  # Return parsed command as a list of token.
  def _buildCmd(self,command):  
    # Build command to execute
    action_cmd = []
    action_cmd.extend(self.cmd_prefix)
    action_cmd.extend(command)
    for i in range(len(action_cmd)):
      action_cmd[i] = self._detokenize(action_cmd[i])
    return action_cmd

  # Parse all variables related to iSCSI proxy in the string passed as argument.
  # Return parsed string.
  # Note that this class must generally be overridden in the derived class to process
  # attributes specific to this class. But it should normally call this method for
  # the common attributes.
  def _detokenize(self,string):    
    if re.search('%%ISCSI_HOST%%',string):
      if self.proxyHost == "local":
        string = re.sub('%%ISCSI_HOST%%',socket.gethostname()+":3260",string)
      else:
        string = re.sub('%%ISCSI_HOST%%',self.proxyHost+":3260",string)
    elif string == '%%ISCSI_PROXY%%':
      string = "%s@%s" % (self.mgtUser,self.proxyHost)
    elif string == '%%PRIVKEY%%':
      string = self.mgtPrivKey
    elif string == '%%VOLUME_NAME%%':
      string = self.volumeName
    return string
