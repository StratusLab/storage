<#include "/html/header.ftl">

<form action="${baseurl}/disks/" enctype="application/x-www-form-urlencoded" method="POST">
    <p> 
        <label for="size">Size (GB):</label>
        <input type="text" name="size" size="10" /> 
    </p>
    <p> 
        <label for="tag">Tag:</label>
        <input type="text" name="tag" size="40" /> 
    </p>
    
    <p>
        <label for="visibility">Visibility:<label>
        <select name="visibility">
            <#list visibilities as visibility>
            <option value="${visibility}">${visibility?capitalize}</option>
            </#list>
        </select>
    </p>
        
    <p>
        <input type="submit" value="Create" />
    </p>
</form>
    
<#include "/html/footer.ftl">
