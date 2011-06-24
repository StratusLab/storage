<#function zebra index>
  <#if (index % 2) == 0>
    <#return "even" />
  <#else>
    <#return "odd" />
  </#if>
</#function>

<#include "/html/header.ftl">

    <#if deleted??>
        <p class="success">
            Your disk have been deleted successfully.
        </p>
    </#if>

    <p class="right"><a href="create/">New disk</a></p>

    <#if disks?has_content>
        <#escape x as x?html>
        <table class="display">  
          <thead>
            <tr>
              <th>Tag</th>
              <th>Size</th>
              <th>UUID</th>
            </tr>
          </thead>
          <tbody>
            <#list disks as disk>
            <tr class="${zebra(disk_index)}">
              <td>${disk.tag!}</td>
              <td class="center">${disk.size} GB</td>
              <td><a href="disks/${disk.uuid}">${disk.uuid}</a></td>
            </tr>
            </#list>
          </tbody>
          <tfoot>
            <tr>
              <th>Tag</th>
              <th>Size</th>
              <th>UUID</th>
            </tr>
          </tfoot>
        </table>
        </#escape>
    <#else> 
            <p>No disk found. Try to <a href="/create/">add one</a>!</p>
    </#if>
    
    <p class="right"><a href="create/">New disk</a></p>
    
<#include "/html/footer.ftl">