#!/bin/bash

DISK_UUID=$1
PORTAL_ADDR=$2

. /etc/stratuslab/pdisk-host.cfg

detatch_nfs() {
    # Nothing to do for NFS sharing
}

detatch_iscsi() {
    # Must contact the server to discover what disks are available.
    local DISCOVER_CMD="sudo $ISCSIADM --mode discovery --type sendtargets --portal $PORTAL_ADDR"
    local DISCOVER_OUT=`$DISCOVER_CMD | grep -m 1 $DISK_UUID`

    if [ "x$DISCOVER_OUT" = "x" ]
    then
        echo "Unable to find disk $DISK_UUID at $PORTAL_ADDR"
        exit 1
    fi

    # Portal informations
    local PORTAL_IP=`echo $DISCOVER_OUT | cut -d ':' -f 1`
    local PORTAL_PORT=`echo $DISCOVER_OUT | cut -d ':' -f 2 | cut -d ',' -f 1`
    local PORTAL=${PORTAL_IP}:${PORTAL_PORT}

    # Disk information
    local DISK=`echo $DISCOVER_OUT | cut -d ' ' -f 2`

    # Detach disk
    local DETACH_CMD="sudo $ISCSIADM --mode node --portal $PORTAL --targetname $DISK --logout"
    $DETACH_CMD
}

if [ "x$PORTAL_ADDR" = "x" ]
then
    echo "usage: $0 DISK_UUID PORTAL_ADDR"
    exit 1
fi

detach_${SHARE_TYPE}

exit 0
