
import sys
import logging

__all__ = ['abort',
           'debug',
           'EXITCODE_PDISK_OP_FAILED']

verbosity = 0

###############################
# Functions to handle logging #
###############################

# Configure loggers and handlers.
# Initially configure only syslog and stderr handler.

logging_source = 'stratuslab-pdisk'
logger = logging.getLogger(logging_source)
logger.setLevel(logging.DEBUG)

# fmt=logging.Formatter("%(asctime)s - %(name)s - %(levelname)s - %(message)s")
fmt = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
# Handler used to report to SVN must display only the message to allow proper XML formatting
svn_fmt = logging.Formatter("%(message)s")

console_handler = logging.StreamHandler()
console_handler.setLevel(logging.DEBUG)
logger.addHandler(console_handler)

EXITCODE_PDISK_OP_FAILED = 2

def abort(msg):
    logger.error("Persistent disk operation failed:\n%s" % (msg))
    sys.exit(EXITCODE_PDISK_OP_FAILED)

def debug(level, msg):
    if level <= verbosity:
        if level == 0:
            logger.info(msg)
        else:
            logger.debug(msg)
