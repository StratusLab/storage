{
  <#assign keys=dict?keys>
  <#list keys as key>
  "${key}" : "${dict[key]?js_string}"<#if key_has_next>,</#if>
  </#list>
}
