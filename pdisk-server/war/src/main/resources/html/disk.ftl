<html>

<head>
  <meta http-equiv="CONTENT-TYPE" CONTENT="text/html; charset=UTF-8">
  <title>StratusLab Persistent Disk Storage</title>
</head>
  
   <body>

    <table>
      <#assign keys=properties?keys>
      <#list keys as key>
      <tr><td>${key}</td><td>${properties[key]}</td></tr>
      </#list>
    </table>
    
    <hr/>

    <form action="./?method=delete" enctype="application/x-www-form-urlencoded" method="POST">
      <table>
        <tr><td><input type="submit" value="delete"></td></tr>
      </table>
    </form>

   </body>
</html>
