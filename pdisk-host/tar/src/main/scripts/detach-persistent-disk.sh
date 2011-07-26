#!/bin/bash

if [ "x$1" = "x" ]
then
    echo "usage: $0 DISK_UUID PORTAL_ADDR"
    exit 1
fi

ISCSIADM=/sbin/iscsiadm
DISK_UUID=$1
PORTAL_ADDR=$2

# Must contact the server to discover what disks are available.
DISCOVER_CMD="sudo $ISCSIADM --mode discovery --type sendtargets --portal $PORTAL_ADDR"
echo $DISCOVER_CMD
DISCOVER_OUT=`$DISCOVER_CMD | grep -m 1 $DISK_UUID`

if [ "x$DISCOVER_OUT" = "x" ]
then
    echo "Unable to find disk $DISK_UUID at $PORTAL_ADDR"
    exit 1
fi

# Portal informations
PORTAL_IP=`echo $DISCOVER_OUT | cut -d ':' -f 1`
PORTAL_PORT=`echo $DISCOVER_OUT | cut -d ':' -f 2 | cut -d ',' -f 1`
PORTAL=${PORTAL_IP}:${PORTAL_PORT}

# Disk information
DISK=`echo $DISCOVER_OUT | cut -d ' ' -f 2`

# Detach disk
DETACH_CMD="$ISCSIADM --mode node --portal $PORTAL --targetname $DISK --logout"
$DETACH_CMD
echo $DETACH_CMD

