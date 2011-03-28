<html>

  <body>

    <hr/>
    
    <form action="../disks/" enctype="multipart/form-data" method="POST">
      <table>
        <tbody>
          <tr>
            <td><input type="file" name="Disk Description" size="40"></td>
          </tr>
          <tr>
            <td><input type="submit" value="Submit"></td>
          </tr>
        </tbody>
      </table>
    </form>
    
    <hr/>

    <form action="../disks/" enctype="application/x-www-form-urlencoded" method="POST">
      <table>
        <tbody>
          <tr>
            <td>Size (GB)</td><td><input type="text" name="size" size="40"></td>
          </tr>
          <tr>
            <td>Tag</td><td><input type="text" name="tag" size="40"></td>
          </tr>
          <tr>
            <td><input type="submit" value="Submit"></td><td></td>
          </tr>
        </tbody>
      </table>
    </form>
    
    <hr/>

  </body>
</html>
