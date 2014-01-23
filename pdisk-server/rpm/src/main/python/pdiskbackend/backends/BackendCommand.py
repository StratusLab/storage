
class BackendCommand(object):
    def __init__(self, command=None, success_patterns=None, failure_command=None,
                 failure_ok_patterns=None, action=None):
        self._command = command
        self._success_patterns = success_patterns
        self._failure_command = failure_command
        self._failure_ok_patterns = failure_ok_patterns
        self._action = action

    @property
    def command(self):
        return self._command
    @command.setter
    def command(self, value):
        self._command = value
    @property
    def success_patterns(self):
        return self._success_patterns
    @property
    def failure_command(self):
        return self._failure_command
    @failure_command.setter
    def failure_command(self, value):
        self._failure_command = value
    @property
    def failure_ok_patterns(self):
        return self._failure_ok_patterns
    @property
    def action(self):
        return self._action

    def run_on_failure(self):
        return self._failure_command and True or False
