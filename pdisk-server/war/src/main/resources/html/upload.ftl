<#include "/html/header.ftl">

<form action="${baseurl}/disks/" enctype="application/x-www-form-urlencoded" method="POST">
    <p> 
        <label for="size">Size (GB):</label>
        <input type="text" name="size" id="size" size="10" /> 
    </p>
    <p> 
        <label for="tag">Tag:</label>
        <input type="text" name="tag" id="tag" size="40" /> 
    </p>
        
    <p>
        <input type="submit" value="Create" />
    </p>
</form>
    
<#include "/html/footer.ftl">
