<#include "/html/header.ftl">

      <#escape x as x?html>
      <#assign keys=properties?keys>
      <dl>
      <#list keys as key>
        <dt>${key?capitalize}:</dt>
        <dd>${properties[key]}</dd>
      </#list>
      </dl>
      </#escape>

    <#if can_delete == true>
    <form action="${url}?method=delete" enctype="application/x-www-form-urlencoded" method="POST">
      <p><input type="submit" value="Delete"></p>
    </form>
    </#if>

<#include "/html/footer.ftl">
