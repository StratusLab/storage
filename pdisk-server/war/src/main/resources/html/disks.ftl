
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

<h2>Create disk</h2>

<form action="${baseurl}/disks/" enctype="application/x-www-form-urlencoded" method="POST">
  <table>
    <thead>
      <tr>
        <th>Size (GiB)</th>
        <th>Visibility</th>
        <th>Tag</th>
        <th></th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>
          <input type="text" name="size" size="10" value="${values.size!}" /> 
        </td>
        <td>
          <select name="visibility">
            <#list visibilities as diskVisibility>
              <option 
                <#if diskVisibility == values.visibility>selected="selected"</#if>
                  value="${diskVisibility}">${diskVisibility?capitalize}</option>
            </#list>
          </select>
        </td>
        <td>
          <input type="text" name="tag" size="40" value="${values.tag!}" /> 
        </td>
        <td>
          <input type="submit" value="Create" />
        </td>
      </tr>
    </tbody>
  </table>
</form>

<hr/>

<h2>Upload existing disk</h2>

<form action="${baseurl}/disks/" enctype="multipart/form-data" method="POST">
  <input type="file" name="Image File" size="40" />
  <input type="submit" value="Upload" />
</form>

<hr/>
<br/>
    
<#if disks?has_content>
  <#escape x as x?html>
    <table class="display">  
      <thead>
        <tr>
          <th>Tag</th>
          <th>Size</th>
          <th>Users</th>
          <th>Owner</th>
          <th>UUID</th>
          <th>Image identifier</th>
        </tr>
      </thead>
      <tbody>
        <#list disks as disk>
          <tr class="${zebra(disk_index)}">
            <td>
              <#if disk.tag?has_content>
                ${disk.tag}
                <#else><em>No tag</em>
              </#if>
            </td>
            <td class="center">${disk.size} GiB</td>
            <td class="center">${disk.usersCount}</td>
            <td class="center">${disk.owner}</td>
            <td><a href="${baseurl}/disks/${disk.uuid}">${disk.uuid}</a></td>
            <td>
              <#if disk.identifier?has_content>
                ${disk.identifier}
              </#if>
            </td>
          </tr>
        </#list>
      </tbody>
    </table>
  </#escape>
<#else> 
  <p>No disks.</p>
</#if>
    
<#include "/html/footer.ftl">
