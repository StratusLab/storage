{
<#escape x as x?js_string>
"node": "${node}",
"vm_id": "${vm_id}",
"uuid": [<#list uuids as disk>"${disk}"<#if disk_has_next>,</#if></#list>]
</#escape>
}