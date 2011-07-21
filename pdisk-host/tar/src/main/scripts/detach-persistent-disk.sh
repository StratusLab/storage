#!/bin/bash

if [ "x$1" = "x" ]
then
    echo "usage: $0 VM_ID"
    exit 1
fi

ISCSIADM=/sbin/iscsiadm
DEVICES_MARKER="/tmp/stratuslab-pdisk-iscsi.prop"
INFO_SEPARATOR="#"

VM_ID=$1

PDISK_PROP=`grep -m 1 ^$VM_ID $DEVICES_MARKER`

# Nothing to do is no pdisk attached
[ "x$PDISK_PROP" = "x" ] && exit 0

# Retrieve disk informations
DISK=`echo $PDISK_PROP | cut -d $INFO_SEPARATOR -f 2`
PORTAL=`echo $PDISK_PROP | cut -d $INFO_SEPARATOR -f 3`

# Detach disk
DETACH_CMD="$ISCSIADM --mode node --portal $PORTAL --targetname $DISK --logout"
$DETACH_CMD
echo $DETACH_CMD

# Remove disk entry
sed -i "s/^${VM_ID}.*//;/^$/d;" $DEVICES_MARKER

