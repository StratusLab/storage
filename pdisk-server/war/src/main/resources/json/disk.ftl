{
  <#assign keys=properties?keys>
  <#list keys as key>
  "${key}" : "${properties[key]?j_string}"<#if key_has_next>,</#if>
  </#list>
}
