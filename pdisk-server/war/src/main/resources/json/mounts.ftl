[
  <#list mounts as mount>
  {
    "vmId" : "${mount.vmId}"
    "device" : "${mount.device}"
  }<#if mount_has_next>,</#if>
  </#list>
]
