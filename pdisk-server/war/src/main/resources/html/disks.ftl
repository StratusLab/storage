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

<form action="${baseurl}/create/" enctype="application/x-www-form-urlencoded" method="POST">
    <p> 
        <label for="size">Size (in GiBs):</label>
        <input type="text" name="size" size="10" value="${values.size}" /> 
    </p>
    <p> 
        <label for="tag">Tag:</label>
        <input type="text" name="tag" size="40" value="${values.tag}" /> 
    </p>
    
    <p>
        <label for="visibility">Visibility:<label>
        <select name="visibility">
            <#list visibilities as diskVisibility>
            <option <#if diskVisibility == values.visibility>selected="selected"</#if> 
                value="${diskVisibility}">${diskVisibility?capitalize}</option>
            </#list>
        </select>
    </p>
        
    <p>
        <input type="submit" value="Create" />
    </p>
</form>

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
            </tr>
          </thead>
          <tbody>
            <#list disks as disk>
            <tr class="${zebra(disk_index)}">
              <td><#if disk.tag == ""><em>No tag</em></#if>${disk.tag!}</td>
              <td class="center">${disk.size} GB</td>
              <td class="center">${disk.users}</td>
              <td class="center">${disk.owner}</td>
              <td><a href="${baseurl}/disks/${disk.uuid}/">${disk.uuid}</a></td>
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
            <p>No disk found. Try to <a href="${baseurl}/create/">add one</a>!</p>
    </#if>
    
    <p class="right"><a href="${baseurl}/create/">New disk</a></p>
    
<#include "/html/footer.ftl">
