<#include "/html/header.ftl">

<p><a href="${url}mounts">View mounts</a> for this disk.</p>

<#escape x as x?html>
  <#assign keys=properties?keys>
  <table>
    <#list keys as key>
      <tr>
        <td><strong>${key?capitalize}</strong></td>
        <td>${properties[key]}</td>
      </tr>
    </#list>

<#if can_delete == true>
  <tr>
    <td>
      <form action="${url}?method=delete" 
            enctype="application/x-www-form-urlencoded" 
            method="POST">
        <input type="submit" value="Delete">
      </form>
    </td>
    <td></td>
  </tr>
</#if>

  </table>
</#escape>


<#include "/html/footer.ftl">
