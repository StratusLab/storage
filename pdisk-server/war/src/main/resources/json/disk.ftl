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
    <#if disk.quarantine?has_content>
    "quarantine": "${disk.quarantine}",
    </#if>
    "size": "${disk.size}",
    "type": "${disk.type}",
    "visibility": "${disk.visibility}"
}
