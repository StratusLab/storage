<#include "/html/header.ftl">

<p>
  <table>
    <tbody>
      <tr>
        <td><strong>Disk ID</strong></td>
        <td><p><a href="${baseurl}/disks/${diskId}">${diskId}</a></p></td>
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
        <td><strong>Device</strong></td>
        <td>${device}</td>
      </tr>
      <tr>
        <td>
          <form action="${currenturl}?method=delete" 
                enctype="application/x-www-form-urlencoded" 
                method="POST">
            <input type="submit" value="Unmount"></p>
          </form>
        </td>
        <td></td>
      </tr>
    </tbody>
  </table>

</p>

<#include "/html/footer.ftl">
