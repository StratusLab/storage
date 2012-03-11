
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
            method="post">
        <input type="submit" value="Delete" />
      </form>
    </td>
    <td></td>
  </tr>
</#if>

  </table>
</#escape>

  <!-- 
  <script language="javascript" src="/css/external/jquery.min.js" type="text/javascript"></script>
  <script language="javascript" type="text/javascript">
    $(document).ready(function() {
      $('td').each(function(i, element) {
        var value = $(element).text();
        value = value.replace('&lt;', '<').replace('&gt;', '>');
        $(element).html(value);
      });
    });
  </script>
  -->

<#include "/html/footer.ftl">
