{
<#escape x as x?js_string>
<#assign keys=properties?keys>
<#list keys as key>
"${key}": "${properties[key]}",
</#list>
</#escape>
}
