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
    
<#include "/html/footer.ftl">
