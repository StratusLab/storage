
<#include "/html/header.ftl">

<#if errors??>
  <ul class="error">
    <#list errors as error>
      <li>${error}.</li>
    </#list>
  </ul>
</#if>

<p>Disk: <a href="${baseurl}disks/${uuid}">${uuid}</a></p>

<p>TURL: ${turl}</p>

<hr/>
<br/>

<#include "/html/footer.ftl">
