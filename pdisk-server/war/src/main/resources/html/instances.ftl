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

<#if instances?has_content>
  <#escape x as x?html>
    <table class="display">  
      <thead>
        <tr>
          <th>Instance Id</th>
        </tr>
      </thead>
      <tbody>
        <#list instances as instance>
          <tr class="${zebra(instance_index)}">
            <td class="center">${instance.vmId}</td>
          </tr>
        </#list>
      </tbody>
    </table>
  </#escape>
<#else> 
  <p>No instances.</p>
</#if>
    
<#include "/html/footer.ftl">
