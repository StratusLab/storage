#!/bin/bash -e

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

register_disk() {
    # We assume here that the disk can be mounted by the user (permission and remaining places)
    local NODE=$(host $(hostname) | awk '{print $4}')
    local REGISTER_CMD="$CURL -k -u ${PDISK_USER}:${PDISK_PSWD} https://${PORTAL}:${PORTAL_PORT}/disks/${DISK_UUID}/mounts/ -d node=${NODE}&vm_id=${VM_ID}&register_only=true"
    set +e
    output=$($REGISTER_CMD 2>&1)
    if [ "$?" != "0" ]; then
        if ( echo $output | grep -q "client certificate not found" ); then 
            set -e
            $REGISTER_CMD
        else
            echo $output
            exit 1
        fi
    fi
    set -e
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
        exit 2
    fi

    # Portal informations
    local PORTAL_IP=`echo $DISCOVER_OUT | cut -d ',' -f 1`

    # Disk informations
    local DISK=`echo $DISCOVER_OUT | cut -d ' ' -f 2` 
    local LUN=`echo $DISCOVER_OUT | cut -d ',' -f 2 | cut -d ' ' -f 1`
    local DISK_PATH="/dev/disk/by-path/ip-$PORTAL_IP-iscsi-$DISK-lun-$LUN"

    # Attach the iSCSI disk on the host.
    local ATTACH_CMD="sudo $ISCSIADM --mode node --portal $PORTAL_IP --targetname $DISK --login"
    $ATTACH_CMD

    # Ensure that the disk device aliases are setup. 
    sleep 2

    # Get the real device name behind the alias and link to it.
    local REAL_DEV=`readlink -e -n $DISK_PATH`
    if [ -z $REAL_DEV ]; then
        sleep 2
        REAL_DEV=`readlink -e -n $DISK_PATH`
        [ -z $REAL_DEV ] && { echo "Couldn't not get real device name behind the alias."; exit 1; }
    fi
    local LINK_CMD="ln -fs $REAL_DEV $DEVICE_LINK"
    $LINK_CMD
}

hotplug_disk() {
    sudo /usr/bin/virsh attach-disk one-$VM_ID $DEVICE_LINK $TARGET
}

echo "$UUID_URL" >> $REGISTER_FILE

attach_${SHARE_TYPE}

if [ "x$TARGET" != "x" ]
then
    hotplug_disk
else
    register_disk
fi

exit 0
