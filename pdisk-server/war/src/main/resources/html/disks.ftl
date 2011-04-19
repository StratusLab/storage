<html>

<head>
  <meta http-equiv="CONTENT-TYPE" CONTENT="text/html; charset=UTF-8">
  <title>StratusLab Persistent Disk Storage</title>
</head>
  
  <body>

    <table>  
      <thead>
        <tr>
          <td>size</td>
          <td>tag</td>
          <td>uuid</td>
        </tr>
      </thead>
      <tbody>
        <#list disks as disk>
        <tr>
          <td>${disk.size}</td>
          <td>${disk.tag!}</td>
          <td><a href="${disk.link}">${disk.uuid}</a></td>
        </tr>
        </#list>
      </tbody>
    </table>

   </body>
</html>
