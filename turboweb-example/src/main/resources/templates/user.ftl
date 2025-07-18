<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>${title}</title>
</head>
<body>
<h1>欢迎 ${username}!</h1>
<p>邮箱: ${email}</p>

<#if articles?? && (articles?size &gt; 0)>
    <h2>文章列表</h2>
    <ul>
        <#list articles as article>
            <li>${article.title} (${article.date})</li>
        </#list>
    </ul>
</#if>
</body>
</html>