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
          <td><strong>Id</strong></td>
          <td>${disk.identifier}</td>
        </tr>
      </#if>
      <#if disk.userscount?has_content>
        <tr>
          <td><strong>User's count</strong></td>
          <td>${disk.userscount}</td>
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
<#escape x as x?html>
  <#assign keys=disk.properties?keys>
    <#list keys as key>
      <tr>
        <td><strong>${key?capitalize}</strong></td>
        <td>${disk.properties[key]}</td>
      </tr>
    </#list>

<#if can_delete == true>
  <tr>
    <td>
      <form action="${currenturl}?method=delete" 
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

  <script language="javascript" src="/css/external/jquery.min.js"></script>
  <script language="javascript" type="text/javascript">
    $(document).ready(function() {
      $('td').each(function(i, element) {
        var value = $(element).text();
        value = value.replace('&lt;', '<').replace('&gt;', '>');
        $(element).html(value);
      });
    });
  </script>

<#include "/html/footer.ftl">
