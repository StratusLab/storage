<#include "/html/header.ftl">

<p>
<strong>Mount Information</strong>
<ul>
<li>diskId: ${diskId}
<li>mountId: ${mountId}
<li>vmId: ${vmId}
<li>node: ${node}
</ul>
</p>

<form action="${url}?method=delete" enctype="application/x-www-form-urlencoded" method="POST">
  <p><input type="submit" value="Unmount"></p>
</form>

<#include "/html/footer.ftl">
