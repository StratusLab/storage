[
  <#list disks as disk>
  {
    "uuid": "${disk.uuid}",
    <#if disk.tag?has_content>
    "tag": "${disk.tag?j_string}",
    <#else>    "tag": "",
    </#if>
    <#if disk.quarantine?has_content>
    "quarantine": "${disk.quarantine}",
    </#if>
    "count": "${disk.usersCount}",
    "owner": "${disk.owner}",
    <#if disk.identifier?has_content>
    "identifier": "${disk.identifier}",
    </#if>
    "size": "${disk.size}"
    "type": "${disk.type}"
  }<#if disk_index < disks?size-1>,</#if>
  </#list>
]
