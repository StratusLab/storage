{
<#escape x as x?js_string>
<#assign keys=properties?keys>
<#list keys as key>
"${key}": "${properties[key]}"<#if key_has_next>,</#if>
</#list>
</#escape>
}
