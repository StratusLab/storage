<html>
  <head>
    <meta http-equiv="CONTENT-TYPE" CONTENT="text/html; charset=utf-8">
    <title>disks</title>
  </head>
  
  <body>

    <ul>  
    <#assign keys=links?keys>
    <#list keys as key>
    <li><a href="${links[key]}">${key}</a>
    </#list>
    </ul>

  </body>
</html>
