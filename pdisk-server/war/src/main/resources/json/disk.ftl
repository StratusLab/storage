{
  <#assign keys=properties?keys>
  <#list keys as key>
  "${key}" : "${properties[key]?js_string}"<#if key_has_next>,</#if>
  </#list>
}
