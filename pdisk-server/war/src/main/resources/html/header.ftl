<!doctype html>
<!--[if lt IE 7 ]> <html lang="en" class="no-js ie6"> <![endif]-->
<!--[if IE 7 ]>    <html lang="en" class="no-js ie7"> <![endif]-->
<!--[if IE 8 ]>    <html lang="en" class="no-js ie8"> <![endif]-->
<!--[if IE 9 ]>    <html lang="en" class="no-js ie9"> <![endif]-->
<!--[if (gt IE 9)|!(IE)]><!--> <html lang="en" class="no-js"> <!--<![endif]-->
<head>
    <base href="${baseurl}"> 
    
    <meta charset="UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    
    <title>${title!}<#if title??> - </#if>StratusLab Persistent Disk Storage</title>
 
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
   
    <link rel="stylesheet" type="text/css" href="${baseurl}/css/stratuslab.css" media="screen" title="Default" /> 
    <link rel="shortcut icon" type="images/x-icon" href="${baseurl}/css/favicon.ico" /> 
</head>
<body>
<header>
    <h1>Persistent Disk Storage</h1>
    <nav>
        <ul>
            <li><a href="${baseurl}/">Home</a></li>
            <li><a href="${baseurl}/disks/">Disks</a></li>
            <li><a href="${baseurl}/vms/">VMs</a></li>
        </ul>
    </nav>
</header>
<div id="content">
    <#if title??>
    <h1>${title}</h1>
    </#if>
    
    <#if success??>
    <p class="success">
        ${success}.
    </p>
    </#if>
