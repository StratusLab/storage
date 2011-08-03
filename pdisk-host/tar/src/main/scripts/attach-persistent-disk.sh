#!/bin/bash

UUID_URL=$1
DEVICE_LINK=$2

. /etc/stratuslab/pdisk-host.cfg

PORTAL=`echo $UUID_URL | cut -d ':' -f 2`
DISK_UUID=`echo $UUID_URL | cut -d ':' -f 3`

register_disk() {
    # We assume here that the disk can be mounted by the user (permission and free place)
    $CURL -
}

attach_nfs() {
    if [ -b "${NFS_LOCATION}/${DISK_UUID}" ]
    then
        echo "Disk $DISK_UUID does not exist on NFS share"
        exit 1
    fi

    local ATTACH_CMD="ln -fs ${NFS_LOCATION}/${DISK_UUID} $DEVICE_LINK"
    $ATTACH_CMD
}

attach_iscsi() {
    # Must contact the server to discover what disks are available.
    local DISCOVER_CMD="sudo $ISCSIADM --mode discovery --type sendtargets --portal $PORTAL"
    DISCOVER_OUT=`$DISCOVER_CMD | grep -m 1 $DISK_UUID`

    if [ "x$DISCOVER_OUT" = "x" ]
    then
        echo "Unable to find disk $DISK_UUID at $PORTAL"
        exit 1
    fi

    # Portal informations
    local PORTAL_IP=`echo $DISCOVER_OUT | cut -d ':' -f 1`
    local PORTAL_PORT=`echo $DISCOVER_OUT | cut -d ':' -f 2 | cut -d ',' -f 1`
    local PORTAL=${PORTAL_IP}:${PORTAL_PORT}

    # Disk informations
    local DISK=`echo $DISCOVER_OUT | cut -d ' ' -f 2` 
    local LUN=`echo $DISCOVER_OUT | cut -d ',' -f 2 | cut -d ' ' -f 1`
    local DISK_PATH="/dev/disk/by-path/ip-$PORTAL-iscsi-$DISK-lun-$LUN"

    # Attach the iSCSI disk on the host.
    local ATTACH_CMD="sudo $ISCSIADM --mode node --portal $PORTAL --targetname $DISK --login"
    $ATTACH_CMD

    # Ensure that the disk device aliases are setup. 
    sleep 2

    # Get the real device name behind the alias and link to it.
    local REAL_DEV=`readlink -e -n $DISK_PATH`
    local LINK_CMD="ln -fs $REAL_DEV $DEVICE_LINK"
    $LINK_CMD
}

if [ "x$DEVICE_LINK" = "x" ]
then
    echo "usage: $0 UUID_URL DEVICE_LINK"
    echo "UUID_URL have to be pdisk:<portal_address>:<disk_uuid>"
    exit 1
fi

attach_${SHARE_TYPE}

exit 0
