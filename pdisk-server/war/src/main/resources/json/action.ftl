{
  "node" : "${node}",
  "vm_id" : "${vm_id}",
  <#if target??>"target" : "${target}",</#if>
  "uuid" : [<#list uuids as disk>"${disk}"<#if disk_has_next>,</#if></#list>]
}
