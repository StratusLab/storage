#!/bin/bash

ISCSIADM=/sbin/iscsiadm

DIR=$1
IMAGE_DIR=$DIR/images

# Hardcoded (for now) portal information. 
PORTAL_IP=134.158.75.2
PORTAL_PORT=3260
PORTAL="$PORTAL_IP:$PORTAL_PORT"

# Loop over all of the *.iscsi.uuid disks to detach them.
shopt -s nullglob
echo $IMAGE_DIR >> /tmp/detach.log
for i in $IMAGE_DIR/*.iscsi.uuid; do
  DISK=`cat $i`
  DETACH_CMD="$ISCSIADM --mode node --portal $PORTAL --targetname $DISK --logout"
  $DETACH_CMD
done
