<#assign keys=properties?keys>
<#list keys as key>
${key}=${properties[key]}
</#list>
