[
  <#list disks as disk>
  {
    <#assign keys=disk?keys>
    <#list keys as key>
    "${key}" : "${disk[key]?j_string}"<#if key_has_next>,</#if>
    </#list>
  }<#if disk_has_next>,</#if>
  </#list>
]
