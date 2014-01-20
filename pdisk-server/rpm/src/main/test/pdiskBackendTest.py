import unittest

from pdiskbackend.backends.Backend import Backend
from pdiskbackend.utils import EXITCODE_PDISK_OP_FAILED

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
            assert [None] * 5 == backend.getCmd(action).next()

if __name__ == "__main__":
    unittest.main()
