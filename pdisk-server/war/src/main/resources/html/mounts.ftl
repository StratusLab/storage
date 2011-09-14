<#function zebra index>
  <#if (index % 2) == 0>
    <#return "even" />
  <#else>
    <#return "odd" />
  </#if>
</#function>

<#include "/html/header.ftl">

<#if errors??>
  <ul class="error">
    <#list errors as error>
      <li>${error}.</li>
    </#list>
  </ul>
</#if>

<form action="${baseurl}/disks/${uuid}/mounts/" enctype="application/x-www-form-urlencoded" method="POST">
  <table>
    <thead>
      <tr>
        <th></th>
        <th>VM ID</th>
        <th>Node</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>
          <input type="submit" value="Mount" />
        </td>
        <td>
          <input type="text" name="vm_id" size="10" value="" /> 
        </td>
        <td>
          <input type="text" name="node" size="40" value="" /> 
        </td>
      </tr>
    </tbody>
  </table>
</form>

<hr/>
<br/>
    
<#if mounts?has_content>
  <#escape x as x?html>
    <table class="display">  
      <thead>
        <tr>
          <th>Mount ID</th>
          <th>Device</th>
        </tr>
      </thead>
      <tbody>
        <#list mounts as mount>
          <tr class="${zebra(mount_index)}">
            <td><a href="${baseurl}/disks/${mount.uuid}/mounts/${mount.mountid}/">${mount.mountid}</a></td>
            <td class="center">${mount.device}</td>
          </tr>
        </#list>
      </tbody>
    </table>
  </#escape>
<#else> 
  <p>No mounts.</p>
</#if>
    
<#include "/html/footer.ftl">
