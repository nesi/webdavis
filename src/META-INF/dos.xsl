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
                <script language="javascript">
                    function blink() {
                        document.all.blink.style.visibility =
                                (document.all.blink.style.visibility == "") ?
                                        "hidden" : "";
                    }
                    function startBlink() {
                        if (document.all) setInterval("blink()", 500);
                    }
                </script>
                <style>
    body {
        font-size: 10pt;
        background: black;
        color: silver;
    }
    pre {
        font-family: Terminal, Fixedsys, monospace;
    }
    a {
        text-decoration: none;
        color: silver;
    }
    a.unc {
        behavior: url(#default#AnchorClick);
    }
    a:hover {
        color: white;
    }
    blink {
        text-decoration: underline blink;
    }
                </style>
            </head>
            <body onload="startBlink()">
                <xsl:apply-templates select="D:multistatus"/>
            </body>
        </html>
    </xsl:template>
    <xsl:template match="D:multistatus">
<pre>
<xsl:apply-templates select="D:response[D:href = $href]" mode="base"/>
<xsl:apply-templates select="D:response[D:href != $href]" mode="entry">
    <xsl:sort select="D:propstat/D:prop/D:displayname"/>
</xsl:apply-templates>
<xsl:call-template name="pad-string">
    <xsl:with-param name="string" select="format-number(count(D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]), '#,##0')"/>
    <xsl:with-param name="length" select="16"/>
</xsl:call-template>
<xsl:text> File(s)</xsl:text>
<xsl:call-template name="pad-string">
    <xsl:with-param name="string" select="format-number(sum(D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]/D:propstat/D:prop/D:getcontentlength), '#,##0')"/>
    <xsl:with-param name="length" select="15"/>
</xsl:call-template>
<xsl:text> bytes</xsl:text>
<xsl:text>
</xsl:text>
<xsl:call-template name="pad-string">
    <xsl:with-param name="string">
        <xsl:choose>
            <xsl:when test="$url != 'smb://'">
                <xsl:value-of select="format-number(count(D:response[D:href != $href][D:propstat/D:prop/D:resourcetype/D:collection]) + 2, '#,##0')"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="format-number(count(D:response[D:href != $href][D:propstat/D:prop/D:resourcetype/D:collection]), '#,##0')"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:with-param>
    <xsl:with-param name="length" select="16"/>
</xsl:call-template>
<xsl:text> Dir(s)

C:\&gt;</xsl:text>
<blink id="blink"><xsl:text>_</xsl:text></blink>
</pre>
    </xsl:template>
    <xsl:template match="D:response" mode="base">
        <xsl:text> Directory of </xsl:text>
        <a class="unc" href="{$href}" folder="{$href}"><xsl:value-of select="$unc"/></a>
        <xsl:text>

</xsl:text>
        <xsl:if test="$url != 'smb://'">
            <a href="./">
                <xsl:call-template name="format-date">
                    <xsl:with-param name="date">
                        <xsl:choose>
                            <xsl:when test="D:propstat/D:prop/D:creationdate">
                                <xsl:value-of select="D:propstat/D:prop/D:creationdate"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>1970-01-01T00:00:00.000Z</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:with-param>
                </xsl:call-template>
                <xsl:text>      &lt;DIR&gt;          .</xsl:text>
            </a>
            <xsl:text>
</xsl:text>
            <a href="../">
                <xsl:call-template name="format-date">
                    <xsl:with-param name="date">
                        <xsl:choose>
                            <xsl:when test="D:propstat/D:prop/D:creationdate">
                                <xsl:value-of select="D:propstat/D:prop/D:creationdate"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>1970-01-01T00:00:00.000Z</xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:with-param>
                </xsl:call-template>
                <xsl:text>      &lt;DIR&gt;          ..</xsl:text>
            </a>
            <xsl:text>
</xsl:text>
        </xsl:if>
    </xsl:template>
    <xsl:template match="D:response[D:propstat/D:prop/D:resourcetype/D:collection]" mode="entry">
        <a href="{D:href}">
            <xsl:call-template name="format-date">
                <xsl:with-param name="date">
                    <xsl:choose>
                        <xsl:when test="D:propstat/D:prop/D:creationdate">
                            <xsl:value-of select="D:propstat/D:prop/D:creationdate"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text>1970-01-01T00:00:00.000Z</xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:with-param>
            </xsl:call-template>
            <xsl:text>      &lt;DIR&gt;          </xsl:text>
            <xsl:value-of select="substring(D:propstat/D:prop/D:displayname, 1, string-length(D:propstat/D:prop/D:displayname) - 1)"/>
        </a>
        <xsl:text>
</xsl:text>
    </xsl:template>
    <xsl:template match="D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]" mode="entry">
        <a href="{D:href}">
            <xsl:call-template name="format-date">
                <xsl:with-param name="date">
                    <xsl:choose>
                        <xsl:when test="D:propstat/D:prop/D:creationdate">
                            <xsl:value-of select="D:propstat/D:prop/D:creationdate"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text>1970-01-01T00:00:00.000Z</xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:with-param>
            </xsl:call-template>
            <xsl:call-template name="pad-string">
                <xsl:with-param name="string" select="format-number(number(D:propstat/D:prop/D:getcontentlength), '#,##0')"/>
                <xsl:with-param name="length" select="20"/>
            </xsl:call-template>
            <xsl:text> </xsl:text>
            <xsl:value-of select="D:propstat/D:prop/D:displayname"/>
        </a>
        <xsl:text>
</xsl:text>
    </xsl:template>
    <xsl:template name="format-date">
        <xsl:param name="date"/>
        <xsl:value-of select="substring($date, 6, 2)"/>
        <xsl:text>/</xsl:text>
        <xsl:value-of select="substring($date, 9, 2)"/>
        <xsl:text>/</xsl:text>
        <xsl:value-of select="substring($date, 1, 4)"/>
        <xsl:text>  </xsl:text>
        <xsl:call-template name="format-time">
            <xsl:with-param name="hour" select="substring($date, 12, 2)"/>
            <xsl:with-param name="minute" select="substring($date, 15, 2)"/>
        </xsl:call-template>
    </xsl:template>
    <xsl:template name="format-time">
        <xsl:param name="hour"/>
        <xsl:param name="minute"/>
        <xsl:choose>
            <xsl:when test="number($hour) &gt; 12">
                <xsl:value-of select="format-number(number($hour) - 12, '00')"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:choose>
                    <xsl:when test="number($hour) = 0">
                        <xsl:text>12</xsl:text>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="format-number(number($hour), '00')"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:otherwise>
        </xsl:choose>
        <xsl:text>:</xsl:text>
        <xsl:value-of select="format-number(number($minute), '00')"/>
        <xsl:choose>
            <xsl:when test="number($hour) &lt; 12">
                <xsl:text>a</xsl:text>
            </xsl:when>
            <xsl:otherwise>
                <xsl:text>p</xsl:text>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    <xsl:template name="pad-string">
        <xsl:param name="string"/>
        <xsl:param name="length"/>
        <xsl:variable name="value" select="concat('                              ', $string)"/>
        <xsl:value-of select="substring($value, string-length($value) - $length + 1)"/>
    </xsl:template>
</xsl:stylesheet>
