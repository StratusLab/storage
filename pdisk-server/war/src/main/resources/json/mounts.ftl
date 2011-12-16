[
  <#list mounts as mount>
  {
    <#assign keys=mount?keys>
    <#list keys as key>
    "${key}" : "${mount[key]}"<#if key_has_next>,</#if>
    </#list>
  }<#if disk_has_next>,</#if>
  </#list>
]
