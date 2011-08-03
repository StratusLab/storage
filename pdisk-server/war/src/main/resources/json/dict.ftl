{
<#escape x as x?js_string>
<#assign keys=dict?keys>
<#list keys as key>
"${key}": "${dict[key]}"<#if key_has_next>,</#if>
</#list>
</#escape>
}