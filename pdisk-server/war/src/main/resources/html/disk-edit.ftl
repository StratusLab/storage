
<#include "/html/header.ftl">

<p><a href="${currenturl}/mounts">View mounts</a> for this disk.</p>
<table>
  <form action="${currenturl}?method=put" 
        enctype="application/x-www-form-urlencoded" 
        method="post">
      <tr>
        <td><strong>UUID</strong></td>
        <td>${disk.uuid}</td>
      </tr>
      <tr>
        <td><strong>Size</strong></td>
        <td>${disk.size}</td>
      </tr>
	      <tr>
	        <td><strong>Tag</strong></td>
	        <td><input name="tag" value="${disk.tag}" /></td>
	      </tr>
        <tr>
          <td><strong>Image identifier</strong></td>
          <td>${disk.identifier}</td>
        </tr>
        <tr>
          <td><strong>User's count</strong></td>
          <td>${disk.usersCount}</td>
        </tr>
	    <tr>
	      <td><strong>Owner</strong></td>
	      <td>${disk.owner}</td>
	    </tr>
        <tr>
          <td><strong>Visibility</strong></td>
          <td>
            <select name="visibility">
                <#list visibilities as diskVisibility>
                  <option 
                    <#if diskVisibility == disk.visibility>selected="selected"</#if>
                      value="${diskVisibility}">${diskVisibility?capitalize}</option>
                </#list>
              </select>
          </td>
        </tr>
      <tr>
        <td><strong>Share</strong></td>
          <td><input name="group" id="group" value="${disk.group}" /></td>
      </tr>
      <#if disk.quarantine?has_content>
        <tr>
          <td><strong>Quarantine start period</strong></td>
          <td>${disk.quarantine}</td>
        </tr>
      </#if>
      <tr>
        <td><strong>Is original (seed)</strong></td>
        <td>
          <input type="checkbox" name="seed" 
            <#if disk.seed>checked="true"</#if>/>
        </td>
      </tr>
      <tr>
        <td><strong>Disk type</strong></td>
        <td>
            <select name="type">
                <#list types as type>
                  <option 
                    <#if type == disk.type>selected="selected"</#if>
                      value="${type}">${type?capitalize}</option>
                </#list>
              </select>
        </td>
      </tr>

	  <tr>
	    <td>
          <input type="button" value="Cancel" onClick="window.location='${currenturl}'">
	      <input type="submit" value="Save" />
	    </td>
	    <td></td>
	  </tr>
    </form>

  </table>

<#include "/html/footer.ftl">
