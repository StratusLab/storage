#!/bin/bash

UUID_URL=$1
DEVICE_LINK=$2
TARGET=$3

if [ "x$DEVICE_LINK" = "x" ]
then
    echo "usage: $0 UUID_URL DEVICE_LINK [TARGET]"
    echo "UUID_URL have to be pdisk:<portal_address>:<portal_port>:<disk_uuid>"
    echo "If TARGET specified, disk will be hot plugged"
    exit 1
fi

. /etc/stratuslab/pdisk-host.cfg

PORTAL=`echo $UUID_URL | cut -d ':' -f 2`
PORTAL_PORT=`echo $UUID_URL | cut -d ':' -f 3`
DISK_UUID=`echo $UUID_URL | cut -d ':' -f 4`
VM_DIR=$(dirname $(dirname $DEVICE_LINK))
VM_ID=`basename $VM_DIR`
REGISTER_FILE="$VM_DIR/$REGISTER_FILENAME"

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
    device_name=`basename $DEVICE_LINK`
    vms_dir=$(dirname $VM_DIR)
    if [ "x$TARGET" != "x" ]; then
    	/usr/sbin/stratus-pdisk-client.py \
            --username $PDISK_USER --password $PDISK_PSWD \
            --pdisk-id $UUID_URL --vm-dir $vms_dir --vm-id $VM_ID \
            --vm-disk-name $UUID_URL \
            --target $TARGET --attach --link --mount --op up
    else
    	/usr/sbin/stratus-pdisk-client.py \
            --username $PDISK_USER --password $PDISK_PSWD \
            --pdisk-id $UUID_URL --vm-dir $vms_dir --vm-id $VM_ID \
            --vm-disk-name $UUID_URL \
            --register --attach --link --op up
    fi
}

echo "$UUID_URL" >> $REGISTER_FILE

attach_${SHARE_TYPE}

exit 0
