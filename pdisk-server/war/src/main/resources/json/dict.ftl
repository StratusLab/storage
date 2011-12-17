{
  <#assign keys=dict?keys>
  <#list keys as key>
  "${key}" : "${dict[key]?j_string}"<#if key_has_next>,</#if>
  </#list>
}
