[
  <#list disks as disk>
  {
    "uuid": "${disk.uuid}",
    <#if disk.tag?has_content>
    "tag": "${disk.tag}",
    <#else>    "tag": "",
    </#if>
    "count": "${disk.usersCount}",
    "owner": "${disk.owner}",
    <#if disk.identifier?has_content>
    "identifier": "${disk.identifier}",
    </#if>
    "size": "${disk.size}"
  }<#if disk_index < disks?size-1>,</#if>
  </#list>
]
