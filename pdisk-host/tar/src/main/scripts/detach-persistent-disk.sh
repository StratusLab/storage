#!/bin/bash

VM_DIR=$1
UUID_URL=$VM_DIR
TARGET=$2
VM_ID=$3

. /etc/stratuslab/pdisk-host.cfg

if [ "x$TARGET" = "x" ]
then
    VM_ID=`basename $VM_DIR`
    REGISTER_FILE="$VM_DIR/$REGISTER_FILENAME"
fi

if [[ "x$VM_DIR" = "x" || "x$VM_ID" = "x" ]]
then
    echo "usage: $0 (VM_DIR|UUID_URL) [TARGET VM_ID]"
    echo "UUID_URL used to detach hotplugged disk"
    echo "UUID_URL have to be pdisk:<portal_address>:<portal_port>:<disk_uuid>"
    echo "TARGET and VM_ID are mandatory for UUID_URL"
    exit 1
fi

deregister_disks() {
    local NODE=`hostname`
    local DEREGISTER_CMD="$CURL -k -u ${PDISK_USER}:${PDISK_PSWD} -X DELETE https://${PORTAL}:${PORTAL_PORT}/disks/${DISK_UUID}/mounts/${VM_ID}-${NODE}"
    echo "$DEREGISTER_CMD"
    $DEREGISTER_CMD
}

detach_nfs() {
    local UUID_URL="$1"
    
    PORTAL=`echo $UUID_URL | cut -d ':' -f 2`
    PORTAL_PORT=`echo $UUID_URL | cut -d ':' -f 3`
    DISK_UUID=`echo $UUID_URL | cut -d ':' -f 4`
}

detach_iscsi() {
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
        return
    fi

    # Portal informations
    local PORTAL_IP=`echo $DISCOVER_OUT | cut -d ',' -f 1`

    # Disk informations
    local DISK=`echo $DISCOVER_OUT | cut -d ' ' -f 2` 
    local LUN=`echo $DISCOVER_OUT | cut -d ',' -f 2 | cut -d ' ' -f 1`
    local DISK_PATH="/dev/disk/by-path/ip-$PORTAL_IP-iscsi-$DISK-lun-$LUN"

    # Detach the disk only if it exists and if no one else is using it
    [ -b $DISK_PATH ] || return
    if [ `sudo /usr/sbin/lsof $DISK_PATH | wc -l` -eq 0 ]
    then
        local DETACH_CMD="sudo $ISCSIADM --mode node --portal $PORTAL_IP --targetname $DISK --logout"
        echo "$DETACH_CMD"
        $DETACH_CMD
    fi
}

detach_all_disks() {
    ATTACHED_DISK="`cat $REGISTER_FILE 2>/dev/null`"

    # if no pdisk attached, nothing to do
    [ "x$ATTACHED_DISK" = "x" ] && return

    for DISK_INFO in ${ATTACHED_DISK[*]}
    do
        echo "detach_${SHARE_TYPE} $DISK_INFO"
        detach_${SHARE_TYPE} $DISK_INFO
        deregister_disks
    done
}

detach_hotplug_disk() {
    sudo /usr/bin/virsh detach-disk one-$VM_ID $TARGET
    detach_${SHARE_TYPE} $UUID_URL
}

if [ "x$TARGET" = "x" ]
then 
    detach_all_disks
else
    detach_hotplug_disk
fi

exit 0
