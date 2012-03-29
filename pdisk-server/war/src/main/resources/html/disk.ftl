
<#include "/html/header.ftl">

<p><a href="${currenturl}/mounts">View mounts</a> for this disk.</p>
<table>
      <tr>
        <td><strong>UUID</strong></td>
        <td>${disk.uuid}</td>
      </tr>
      <tr>
        <td><strong>Size</strong></td>
        <td>${disk.size}</td>
      </tr>
      <#if disk.tag?has_content>
	      <tr>
	        <td><strong>Tag</strong></td>
	        <td>${disk.tag}</td>
	      </tr>
      </#if>
      <#if disk.identifier?has_content>
        <tr>
          <td><strong>Image identifier</strong></td>
          <td>${disk.identifier}</td>
        </tr>
      </#if>
      <#if disk.usersCount?has_content>
        <tr>
          <td><strong>User's count</strong></td>
          <td>${disk.usersCount}</td>
        </tr>
      </#if>
      <#if disk.owner?has_content>
        <tr>
          <td><strong>Owner</strong></td>
          <td>${disk.owner}</td>
        </tr>
      </#if>
      <#if disk.visibility?has_content>
        <tr>
          <td><strong>Visibility</strong></td>
          <td>${disk.visibility}</td>
        </tr>
      </#if>
      <#if disk.quarantine?has_content>
        <tr>
          <td><strong>Quarantine start period</strong></td>
          <td>${disk.quarantine}</td>
        </tr>
      </#if>
      <tr>
        <td><strong>Is original (seed)</strong></td>
        <td><input type="checkbox" disabled="true" 
        <#if disk.seed> 
        checked="true"
        </#if>
        /></td>
      </tr>
      <tr>
        <td><strong>Disk type</strong></td>
          <td>${disk.type}</td>
      </tr>
      <tr>
        <td><strong>Disk visibility</strong></td>
          <td>${disk.visibility}</td>
      </tr>

<#if can_delete == true>
  <tr>
    <td>
      <form action="${currenturl}?method=delete" 
            enctype="application/x-www-form-urlencoded" 
            method="post">
        <input type="submit" value="Delete" />
      </form>
    </td>
    <td></td>
  </tr>
</#if>

  </table>

<#include "/html/footer.ftl">
