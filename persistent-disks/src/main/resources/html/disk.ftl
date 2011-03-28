<html>
  <head>
    <meta http-equiv="CONTENT-TYPE" CONTENT="text/html; charset=utf-8">
    <title>Upload Disk Description</title>
  </head>
  <body>

    <table>
      <#assign keys=properties?keys>
      <#list keys as key>
      <tr><td>${key}</td><td>${properties[key]}</td></tr>
      </#list>
    </table>
    
    <br>

    <form action="./?method=delete" enctype="application/x-www-form-urlencoded" method="POST">
      <table>
        <tr><td><input type="submit" value="delete"></td></tr>
      </table>
    </form>

  </body>
</html>
