<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">

<html xmlns="http://www.w3.org/1999/xhtml">

<head>

  <meta http-equiv="content-type" content="text/html;charset=UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
   
  <title>StratusLab ${title}</title>

  <base href="${baseurl}" /> 

<style type="text/css">
#content {
    width: 26em;
    margin: 2em auto;
}
.error {
    padding: 0.8em; 
    margin-bottom: 1em; 
    border: 2px solid #fbc2c4;
    background: #fbe3e4; 
    color: #8a1f11;
}
label {
    display: block;
    width: 8em;
    float: left;
}
input[type=text],
input[type=password] {
    width: 17em;
}

</style>

</head>

<body>

<div id="content">

<h1>StratusLab ${title}</h1>


<#if error??>
  <div class="error">${error}</div>
</#if>

<form action="${pageurl}" method="post" id="login-form">

  <p>
    <label for="id_username">Username:</label> <input type="text" name="username" id="id_username" />
  </p>

  <p>
    <label for="id_password">Password:</label> <input type="password" name="password" id="id_password" />
  </p>
  <p>
    <label>&nbsp;</label><input type="submit" value="Log in" />
  </p>
</form>

</div> 
  
</body>
</html>
