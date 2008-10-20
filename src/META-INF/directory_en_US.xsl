<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:D="DAV:">
    <xsl:output method="html"/>
    <xsl:param name="href"/>
    <xsl:param name="url"/>
    <xsl:param name="unc"/>
    <xsl:template match="/">
        <html>
            <head>
                <title>Davenport - <xsl:value-of select="$url"/></title>
                <meta HTTP-EQUIV="Pragma" CONTENT="no-cache"/>
                <meta HTTP-EQUIV="Cache-Control" CONTENT="no-cache"/>
                <meta HTTP-EQUIV="Expires" CONTENT="0"/>
                <style>
    body {
        font-family: Verdana, Tahoma, Helvetica, Arial, sans-serif;
        background: white;
        font-size: 10pt;
    }
    p {
        font-size: 10pt;
    }
    td {
        font-family: Verdana, Tahoma, Helvetica, Arial, sans-serif;
        font-size: 10pt;
    }
    a {
        font-family: Verdana, Tahoma, Helvetica, Arial, sans-serif;
        color: black;
        text-decoration: none;
    }
    a:hover {
        color: green;
    }
    a.hidden {
        font-style: italic;
    }
    a.directory {
        font-weight: bold;
        color: green;
    }
    a.directory:hover {
        color: black;
    }
    a.hiddendirectory {
        font-weight: bold;
        color: #99aa88;
    }
    a.hiddendirectory:hover {
        color: #777777;
    }
    a.parent {
        font-weight: bold;
        color: green;
    }
    a.parent:hover {
        color: #bbccaa;
    }
    .properties {
        font-size: 8pt;
    }
    a.title {
        behavior: url(#default#AnchorClick);
        font-size: 16pt;
        font-weight: bold;
        color: green;
    }
    a.title:hover {
        color: #bbccaa;
    }
    a.unc {
        behavior: url(#default#AnchorClick);
        font-size: 10pt;
        font-weight: bold;
        color: black;
    }
    a.unc:hover {
        color: green;
    }
                </style>
            </head>
            <body>
                <xsl:apply-templates select="D:multistatus"/>
            </body>
        </html>
    </xsl:template>
    <xsl:template match="D:multistatus">
        <xsl:apply-templates select="D:response[D:href = $href]" mode="base"/>
        <xsl:choose>
            <xsl:when test="D:response[D:href != $href]">
                <p>
                    <xsl:text>Total </xsl:text>
                    <xsl:value-of select="format-number(sum(D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]/D:propstat/D:prop/D:getcontentlength), '#,##0 bytes')"/>
                    <xsl:text> (</xsl:text>
                    <xsl:value-of select="format-number(round(sum(D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]/D:propstat/D:prop/D:getcontentlength) div 1024), '#,##0 KB')"/>
                    <xsl:text>).</xsl:text><br/>
                    <xsl:value-of select="format-number(count(D:response[D:href != $href]), '#,##0')"/>
                    <xsl:text> objects (</xsl:text>
                    <xsl:value-of select="format-number(count(D:response[D:href != $href][D:propstat/D:prop/D:resourcetype/D:collection]), '#,##0')"/>
                    <xsl:text> directories, </xsl:text>
                    <xsl:value-of select="format-number(count(D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]), '#,##0')"/>
                    <xsl:text> files):</xsl:text>
                </p>
                <table border="0" cellpadding="0" cellspacing="0">
                    <tr valign="top">
                        <xsl:if test="D:response[D:href != $href][D:propstat/D:prop/D:resourcetype/D:collection]">
                            <td>
                                <table border="0" cellpadding="6" cellspacing="0">
                                    <tr valign="top">
                                        <td nowrap="nowrap">
                                            <xsl:apply-templates select="D:response[D:href != $href][D:propstat/D:prop/D:resourcetype/D:collection]" mode="directory">
                                                <xsl:sort select="D:propstat/D:prop/D:displayname"/>
                                            </xsl:apply-templates>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </xsl:if>
                        <xsl:if test="D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]">
                            <td>
                                <table border="0" cellpadding="6" cellspacing="0">
                                    <xsl:apply-templates select="D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]" mode="file">
                                        <xsl:sort select="D:propstat/D:prop/D:displayname"/>
                                    </xsl:apply-templates>
                                </table>
                            </td>
                        </xsl:if>
                    </tr>
                </table>
            </xsl:when>
            <xsl:otherwise>
                <p>
                    <i>(Directory is empty)</i>
                </p>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    <xsl:template match="D:response" mode="base">
        <p>
            <a class="title" href="{$href}" folder="{$href}"><xsl:value-of select="$url"/></a><br/>
            <a class="unc" href="{$href}" folder="{$href}"><xsl:value-of select="$unc"/></a><br/>
            <xsl:text>Last modified on </xsl:text>
            <xsl:value-of select="D:propstat/D:prop/D:getlastmodified"/>
            <xsl:text>.</xsl:text>
            <xsl:if test="$url != 'smb://'">
                <br/><a href="." class="parent">Parent</a>
            </xsl:if>
        </p>
    </xsl:template>
    <xsl:template match="D:response" mode="directory">
        <xsl:if test="position() != 1"><br/></xsl:if>
        <a href="{D:href}" class="directory">
            <xsl:if test="D:propstat/D:prop/D:ishidden = '1'">
                <xsl:attribute name="class">hiddendirectory</xsl:attribute>
            </xsl:if>
            <xsl:value-of select="D:propstat/D:prop/D:displayname"/>
        </a>
    </xsl:template>
    <xsl:template match="D:response" mode="file">
        <tr valign="top">
            <td nowrap="nowrap">
                <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#eeffdd</xsl:attribute>
                </xsl:if>
                <a href="{D:href}">
                    <xsl:if test="D:propstat/D:prop/D:ishidden = '1'">
                        <xsl:attribute name="class">hidden</xsl:attribute>
                    </xsl:if>
                    <xsl:value-of select="D:propstat/D:prop/D:displayname"/>
                </a>
            </td>
            <td align="right">
                <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#eeffdd</xsl:attribute>
                </xsl:if>
                <xsl:choose>
                    <xsl:when test="number(D:propstat/D:prop/D:getcontentlength) > 1024">
                        <xsl:value-of select="format-number(round(number(D:propstat/D:prop/D:getcontentlength) div 1024), '#,##0 KB')"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="format-number(D:propstat/D:prop/D:getcontentlength, '#,##0 bytes')"/>
                    </xsl:otherwise>
                </xsl:choose>
            </td>
            <td class="properties">
                <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#ddeecc</xsl:attribute>
                </xsl:if>
                <xsl:apply-templates select="D:propstat/D:prop" mode="properties"/>
            </td>
        </tr>
    </xsl:template>
    <xsl:template match="D:prop" mode="properties">
        <xsl:value-of select="D:getlastmodified"/>
        <xsl:if test="D:getcontenttype != 'application/octet-stream'">
            <br/><xsl:value-of select="D:getcontenttype"/>
        </xsl:if>
        <xsl:if test="D:isreadonly = '1'">
            <br/><xsl:text>Read-Only</xsl:text>
        </xsl:if>
        <xsl:if test="D:ishidden = '1'">
            <br/><i><xsl:text>Hidden</xsl:text></i>
        </xsl:if>
    </xsl:template>
</xsl:stylesheet>
