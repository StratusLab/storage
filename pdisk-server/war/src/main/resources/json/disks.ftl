[
<#escape x as x?js_string>
<#list disks as disk>
    {
        "uuid": "${disk.uuid}",
        "tag": "${disk.tag!}",
        "size": ${disk.size},
    },
</#list>
</#escape>
]
