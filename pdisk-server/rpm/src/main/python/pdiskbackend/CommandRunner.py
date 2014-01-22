import re
from subprocess import *

from .utils import debug, abort

#######################################################
# Class representing a command passed to the back-end #
#######################################################
    
class CommandRunner(object):
  cmd_output_start = '<<<<<<<<<<'
  cmd_output_end = '>>>>>>>>>>'
  
  RETRY_ERRORS = [(255, re.compile('^Connection to .* closed by remote host.'))]
  MAX_RETRIES = 3

  def __init__(self,action,cmd,successMsgs=None,failureOkMsgs=None):
    self.action = action
    self.action_cmd = cmd
    self.successMsgs = successMsgs
    self.failureOkMsgs = failureOkMsgs
    self.proc = None

  def execute(self):
    status = 0
    # Execute command: NetApp command don't return an exit code. When a command is sucessful,
    # its output is empty.
    #action_cmd = 'echo ' + self.action_cmd
    debug(1,"Executing command: '%s'" % (' '.join(self.action_cmd)))
    try:
      self.proc = Popen(self.action_cmd, shell=False, stdout=PIPE, stderr=STDOUT)
    except OSError, details:
      abort('Failed to execute %s action: %s' % (self.action,details))
      status = 1
    return status
  
  def checkStatus(self):
    optInfo = None
    try:
      retcode, output = self._getStatusOutputOrRetry()
      output = output.strip()
      if retcode != 0 and output:
          debug(0, '1 An error occurred during %s action (%s). Command output:\n%s\n%s\n%s' % \
                     (self.action, retcode, self.cmd_output_start, output, self.cmd_output_end))
          # In some cases we are OK when failure happens.
          for failurePattern in self.failureOkMsgs.get(self.action, []):
             output_regexp = re.compile(failurePattern, re.MULTILINE)
             matcher = output_regexp.search(output)
             if matcher:
               retcode = 0
               debug(0, '... But we OK to proceed. Setting retcode to 0.')
               break
      else:
          # Need to check if the command is expected to return an output when successfull
          success = False
          if self.successMsgs:
            for successPattern in self.successMsgs:
              output_regexp = re.compile(successPattern, re.MULTILINE)
              matcher = output_regexp.search(output)
              if matcher:
                # Return only the first capturing group
                if output_regexp.groups > 0:
                  optInfo = matcher.groups()
                success = True
                break
          else:
            if len(output) == 0:
              success = True
          if success:
            debug(1,'%s action completed successfully.' % (self.action))
            if len(output) > 0:
              debug(2,'Command output:\n%s\n%s\n%s' % (self.cmd_output_start,output,self.cmd_output_end))
          else:
            retcode = -1
            debug(0,'2 An error occured during %s action. Command output:\n%s\n%s\n%s' % \
                      (self.action,self.cmd_output_start,output,self.cmd_output_end))
    except OSError, details:
      abort('Failed to execute %s action: %s' % (self.action,details))  
    if self.action in ['map', 'delete'] and retcode == 255 and not output.strip():
      retcode = 0
    if retcode == 255:
      if self.action in ['map', 'delete'] and not output.strip():
        retcode = 0
      if self.action in ['unmap']:  
        retcode = 0
    return retcode,optInfo

  def _getStatusOutputOrRetry(self):
    retcode, output = self._getStatusOutput()
    return self._retryOnError(retcode, output)

  def _getStatusOutput(self):
    retcode = self.proc.wait()
    return retcode, self.proc.communicate()[0]

  def _retryOnError(self, retcode, output):
    retries = 0
    while self._needToRetry(retcode, output) and retries < self.MAX_RETRIES:
        time.sleep(1)
        self.execute()
        retcode, output = self._getStatusOutput()
        retries += 1
    return retcode, output

  def _needToRetry(self, retcode, output):
    if retcode == 0:
      return False
    for rc, re_out in self.RETRY_ERRORS:
        if rc == retcode and re_out.match(output):
            return True
    return False
