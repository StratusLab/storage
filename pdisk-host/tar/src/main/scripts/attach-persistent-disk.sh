#!/bin/bash

ISCSIADM=/sbin/iscsiadm

UUID_URL=$1
DEVICE_LINK=$2
DEVICE_MARKER=$DEVICE_LINK.iscsi.uuid

# Hardcoded (for now) portal information. 
PORTAL_IP=134.158.75.2 # onehost-2.lal.in2p3.fr
PORTAL_PORT=3260
PORTAL="$PORTAL_IP:$PORTAL_PORT"

# Disk name information. 
DISK_PREFIX="iqn.2011-01.eu.stratuslab"
DISK_UUID=`echo $UUID_URL | cut -d : -f 2`
DISK="$DISK_PREFIX:$DISK_UUID"

DISK_PATH="/dev/disk/by-path/ip-$PORTAL-iscsi-$DISK-lun-1"

# Must contact the server to discover what disks are available.
DISCOVER_CMD="sudo $ISCSIADM --mode discovery --type sendtargets --portal $PORTAL"
echo $DISCOVER_CMD
$DISCOVER_CMD

# Attach the iSCSI disk on the host.
ATTACH_CMD="sudo $ISCSIADM --mode node --portal $PORTAL --targetname $DISK --login"
echo $ATTACH_CMD
$ATTACH_CMD

# Ensure that the disk device aliases are setup. 
sleep 2

# Get the real device name behind the alias and link to it.
REAL_DEV=`readlink -e -n $DISK_PATH`
LINK_CMD="ln -fs $REAL_DEV $DEVICE_LINK"
echo $LINK_CMD
$LINK_CMD

echo $DISK > $DEVICE_MARKER
