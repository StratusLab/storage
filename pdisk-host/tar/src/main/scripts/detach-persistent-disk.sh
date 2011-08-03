#!/bin/bash

UUID_URL=$1

. /etc/stratuslab/pdisk-host.cfg

PORTAL=`echo $UUID_URL | cut -d ':' -f 2`
PORTAL_PORT=`echo $UUID_URL | cut -d ':' -f 3`
DISK_UUID=`echo $UUID_URL | cut -d ':' -f 4`
VM_ID=`basename $(dirname $(dirname $DEVICE_LINK))`

deregister_disk() {
    local NODE=`hostname`
    # We assume here that the disk can be mounted by the user (permission and remaning places)
    DEREGISTER_CMD="$CURL -k -u ${PDISK_USER}:${PDISK_PSWD} https://${PORTAL}:${PORTAL_PORT}/api/detach" \
                 " -d \"node=${NODE}&vm_id=${VM_ID}\""
    $DEREGISTER_CMD
}

detatch_nfs() {
    # Nothing to do for NFS sharing
}

detatch_iscsi() {
    # Must contact the server to discover what disks are available.
    local DISCOVER_CMD="sudo $ISCSIADM --mode discovery --type sendtargets --portal $PORTAL"
    local DISCOVER_OUT=`$DISCOVER_CMD | grep -m 1 $DISK_UUID`

    if [ "x$DISCOVER_OUT" = "x" ]
    then
        echo "Unable to find disk $DISK_UUID at $PORTAL"
        exit 1
    fi

    # Portal informations
    local PORTAL_IP=`echo $DISCOVER_OUT | cut -d ', ' -f 1`

    # Disk information
    local DISK=`echo $DISCOVER_OUT | cut -d ' ' -f 2`

    # Detach disk
    local DETACH_CMD="sudo $ISCSIADM --mode node --portal $PORTAL_IP --targetname $DISK --logout"
    $DETACH_CMD
}

if [ "x$UUID_URL" = "x" ]
then
    echo "usage: $0 UUID_URL"
    echo "UUID_URL have to be pdisk:<portal_address>:<portal_port>:<disk_uuid>"
    exit 1
fi

detach_${SHARE_TYPE}
deregister_disk

exit 0
