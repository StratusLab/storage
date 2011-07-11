[
<#escape x as x?js_string>
<#list disks as disk>
    {
        <#assign keys=disk?keys>
        <#list keys as key>
        "${key}": "${disk[key]}"<#if key_has_next>,</#if>
        </#list>
    }<#if disk_has_next>,</#if>
</#list>
</#escape>
]
