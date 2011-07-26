#!/bin/bash

if [ "x$2" = "x" ]
then
    echo "usage: $0 UUID_URL DEVICE_PATH"
    echo "UUID_URL have to be pdisk:<portal_address>:<disk_uuid>"
    exit 1
fi

ISCSIADM=/sbin/iscsiadm

UUID_URL=$1
DEVICE_LINK=$2

PORTAL=`echo $UUID_URL | cut -d ':' -f 2`
DISK_UUID=`echo $UUID_URL | cut -d ':' -f 3`

# Must contact the server to discover what disks are available.
DISCOVER_CMD="sudo $ISCSIADM --mode discovery --type sendtargets --portal $PORTAL"
echo $DISCOVER_CMD
DISCOVER_OUT=`$DISCOVER_CMD | grep -m 1 $DISK_UUID`

if [ "x$DISCOVER_OUT" = "x" ]
then
    echo "Unable to find disk $DISK_UUID at $PORTAL"
    exit 1
fi

# Portal informations
PORTAL_IP=`echo $DISCOVER_OUT | cut -d ':' -f 1`
PORTAL_PORT=`echo $DISCOVER_OUT | cut -d ':' -f 2 | cut -d ',' -f 1`
PORTAL=${PORTAL_IP}:${PORTAL_PORT}

# Disk information
DISK=`echo $DISCOVER_OUT | cut -d ' ' -f 2` 
LUN=`echo $DISCOVER_OUT | cut -d ',' -f 2 | cut -d ' ' -f 1`
DISK_PATH="/dev/disk/by-path/ip-$PORTAL-iscsi-$DISK-lun-$LUN"
echo $DISK_PATH

# Attach the iSCSI disk on the host.
ATTACH_CMD="sudo $ISCSIADM --mode node --portal $PORTAL --targetname $DISK --login"
$ATTACH_CMD
echo $ATTACH_CMD

# Ensure that the disk device aliases are setup. 
sleep 2

# Get the real device name behind the alias and link to it.
REAL_DEV=`readlink -e -n $DISK_PATH`
LINK_CMD="ln -fs $REAL_DEV $DEVICE_LINK"
$LINK_CMD

