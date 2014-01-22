import unittest

from pdiskbackend.backends.Backend import Backend
from pdiskbackend.utils import EXITCODE_PDISK_OP_FAILED
from pdiskbackend.backends.BackendCommand import BackendCommand

class BackendTest(unittest.TestCase):

    def setUp(self):
        pass

    def tearDown(self):
        pass
    
    def test_getCmd_missing_action(self):
        backend = Backend()
        try:
            backend.getCmd('').next()
        except SystemExit as ex:
            assert ex.code == EXITCODE_PDISK_OP_FAILED
        else:
            self.fail('Should have raised SystemExit')

    def test_getCmd_default_actions(self):
        for action in Backend.lun_backend_cmd_mapping.keys():
            backend = Backend()
            assert None == backend.getCmd(action).next()

    def test_getCmd(self):
        _lun_backend_cmd_mapping = Backend.lun_backend_cmd_mapping.copy()
        _backend_cmds = Backend.backend_cmds.copy()

        Backend.lun_backend_cmd_mapping.update({'check': ['foo']})
        Backend.backend_cmds = {'foo': ['bar']}
        try:
            backend = Backend()
            backendCmd = backend.getCmd('check').next()
    
            assert None != backendCmd
            assert isinstance(backendCmd, BackendCommand)
            
            assert ['bar'] == backendCmd.parsed_command
        finally:
            Backend.lun_backend_cmd_mapping = _lun_backend_cmd_mapping
            Backend.backend_cmds = _backend_cmds

if __name__ == "__main__":
    unittest.main()
