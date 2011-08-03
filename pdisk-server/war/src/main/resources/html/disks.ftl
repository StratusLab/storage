<#function zebra index>
  <#if (index % 2) == 0>
    <#return "even" />
  <#else>
    <#return "odd" />
  </#if>
</#function>

<#include "/html/header.ftl">

    <p class="right"><a href="${baseurl}/create/">New disk</a></p>

    <#if disks?has_content>
        <#escape x as x?html>
        <table class="display">  
          <thead>
            <tr>
              <th>Tag</th>
              <th>Size</th>
              <th>Owner</th>
              <th>UUID</th>
            </tr>
          </thead>
          <tbody>
            <#list disks as disk>
            <tr class="${zebra(disk_index)}">
              <td><#if disk.tag == ""><em>No tag</em></#if>${disk.tag!}</td>
              <td class="center">${disk.size} GB</td>
              <td class="center">${disk.owner}</td>
              <td><a href="${baseurl}/disks/${disk.uuid}/">${disk.uuid}</a></td>
            </tr>
            </#list>
          </tbody>
          <tfoot>
            <tr>
              <th>Tag</th>
              <th>Size</th>
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
