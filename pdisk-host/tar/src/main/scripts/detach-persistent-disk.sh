#!/bin/bash

VM_IMG_DIR="$1"

. /etc/stratuslab/pdisk-host.cfg

VM_DIR=`dirname $VM_IMG_DIR`
VM_ID=`basename $VM_DIR`
REGISTER_FILE="$VM_DIR/$REGISTER_FILENAME"

deregister_disk() {
    local NODE=`hostname`
    local DEREGISTER_CMD="$CURL -k -u ${PDISK_USER}:${PDISK_PSWD} https://${PORTAL}:${PORTAL_PORT}/api/detach" \
                 " -d \"node=${NODE}&vm_id=${VM_ID}\""
    $DEREGISTER_CMD
}

detatch_nfs() {
    # Nothing to do for NFS sharing
}

detatch_iscsi() {
    local UUID_URL="$1"
    
    PORTAL=`echo $UUID_URL | cut -d ':' -f 2`
    PORTAL_PORT=`echo $UUID_URL | cut -d ':' -f 3`
    DISK_UUID=`echo $UUID_URL | cut -d ':' -f 4`

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

    # Disk name
    local DISK=`echo $DISCOVER_OUT | cut -d ' ' -f 2`

    # Detach the disk only if no one else is using it
    if [ `lsof /dev/disk/by-path/$DISK | wc -l` -eq 2 ]
    then
        local DETACH_CMD="sudo $ISCSIADM --mode node --portal $PORTAL_IP --targetname $DISK --logout"
        $DETACH_CMD
    fi
}

if [ "x$VM_IMG_DIR" = "x" ]
then
    echo "usage: $0 VM_IMG_DIR"
    exit 1
fi

ATTACHED_DISK="`cat $REGISTER_FILE`"

for DISK_INFO in ${ATTACHED_DISK[*]}
do
    detach_${SHARE_TYPE} $DISK_INFO
    deregister_disk
done

exit 0
