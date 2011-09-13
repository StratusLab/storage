<html>

<head>

  <meta http-equiv="CONTENT-TYPE" CONTENT="text/html; charset=utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
   
  <title>${title!}</title>

  <base href="${baseurl}"> 

  <link rel="shortcut icon" href="css/favicon.ico"/>
  <link rel="stylesheet" type="text/css" href="css/stratuslab.css"/>

</head>

<body>
<div class="Page">
  <div class="Header">
     <div class="Banner">
     </div>
  </div>

  <#include "breadcrumbs.ftl">

<div id="content">

  <#if title??>
    <h1>${title}</h1>
  </#if>
    
  <#if success??>
    <p class="success">${success}.</p>
  </#if>

  