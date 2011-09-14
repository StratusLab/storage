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

<form action="${baseurl}/mounts/" enctype="application/x-www-form-urlencoded" method="POST">
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
          <th>Tag</th>
          <th>Size</th>
          <th>Users</th>
          <th>Owner</th>
          <th>UUID</th>
        </tr>
      </thead>
      <tbody>
        <#list mounts as mount>
          <tr class="${zebra(mount_index)}">
            <td><#if mount.tag == ""><em>No tag</em></#if>${mount.tag!}</td>
            <td class="center">${mount.size} GiB</td>
            <td class="center">${mount.users}</td>
            <td class="center">${mount.owner}</td>
            <td><a href="${baseurl}/mounts/${mount.uuid}/">${mount.uuid}</a></td>
          </tr>
        </#list>
      </tbody>
      <tfoot>
        <tr>
          <th>Tag</th>
          <th>Size</th>
          <th>Users</th>
          <th>Owner</th>
          <th>UUID</th>
        </tr>
      </tfoot>
    </table>
  </#escape>
<#else> 
  <p>No mounts.</p>
</#if>
    
<#include "/html/footer.ftl">
