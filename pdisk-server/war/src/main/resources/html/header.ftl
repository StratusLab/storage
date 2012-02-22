<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">

<html xmlns="http://www.w3.org/1999/xhtml">

<head>

  <meta http-equiv="content-type" content="text/html;charset=UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
   
  <title>${title!}</title>

  <base href="${baseurl}" /> 

  <link rel="shortcut icon" href="css/favicon.ico"/>
  <link rel="stylesheet" type="text/css" href="${baseurl}/css/stratuslab.css"/>

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

  