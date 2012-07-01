#!/bin/bash -e

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

detach_nfs() {
    # No-op
}

detach_iscsi() {
    local UUID_URL="$1"
    
    vms_dir=$(dirname $(dirname $VM_ID))
    /usr/sbin/stratus-pdisk-client.py \
        --username $PDISK_USER --password $PDISK_PSWD \
        --pdisk-id $UUID_URL --vm-id $VM_ID \
        --register --attach --op down
}

detach_all_disks() {
    ATTACHED_DISK="`cat $REGISTER_FILE | sort -u 2>/dev/null`"

    # if no pdisk attached, nothing to do
    [ "x$ATTACHED_DISK" = "x" ] && return

    for DISK_INFO in ${ATTACHED_DISK[*]}
    do
        echo "detach_${SHARE_TYPE} $DISK_INFO"
        detach_${SHARE_TYPE} $DISK_INFO
    done
}

if [ "x$TARGET" = "x" ]; then 
    detach_all_disks
fi

exit 0
