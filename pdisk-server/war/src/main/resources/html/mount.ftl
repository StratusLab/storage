
<#include "/html/header.ftl">

<p>
  <table>
    <tbody>
      <tr>
        <td><strong>Disk UUID</strong></td>
        <td>${diskId}</td>
      </tr>
      <tr>
        <td><strong>Mount ID</strong></td>
        <td>${mountId}</td>
      </tr>
      <tr>
        <td><strong>VM ID</strong></td>
        <td>${vmId}</td>
      </tr>
      <tr>
        <td><strong>Node</strong></td>
        <td>${node}</td>
      </tr>
      <tr>
        <td>
          <form action="${url}?method=delete" 
                enctype="application/x-www-form-urlencoded" 
                method="post">
            <input type="submit" value="Unmount"></p>
          </form>
        </td>
        <td></td>
      </tr>
    </tbody>
  </table>

</p>

<#include "/html/footer.ftl">
