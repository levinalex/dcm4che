<?xml version="1.0" encoding="UTF-8"?>
<!-- ***** BEGIN LICENSE BLOCK *****
   - Version: MPL 1.1/GPL 2.0/LGPL 2.1
   -
   - The contents of this file are subject to the Mozilla Public License Version
   - 1.1 (the "License"); you may not use this file except in compliance with
   - the License. You may obtain a copy of the License at
   - http://www.mozilla.org/MPL/
   -
   - Software distributed under the License is distributed on an "AS IS" basis,
   - WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
   - for the specific language governing rights and limitations under the
   - License.
   -
   - The Original Code is part of dcm4che, an implementation of DICOM(TM) in
   - Java(TM), hosted at https://github.com/gunterze/dcm4che.
   -
   - The Initial Developer of the Original Code is
   - Agfa Healthcare.
   - Portions created by the Initial Developer are Copyright (C) 2011
   - the Initial Developer. All Rights Reserved.
   -
   - Contributor(s):
   - Gunter Zeilinger <gunterze@gmail.com>
   -
   - Alternatively, the contents of this file may be used under the terms of
   - either the GNU General Public License Version 2 or later (the "GPL"), or
   - the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
   - in which case the provisions of the GPL or the LGPL are applicable instead
   - of those above. If you wish to allow use of your version of this file only
   - under the terms of either the GPL or the LGPL, and not to allow others to
   - use your version of this file under the terms of the MPL, indicate your
   - decision by deleting the provisions above and replace them with the notice
   - and other provisions required by the GPL or the LGPL. If you do not delete
   - the provisions above, a recipient may use your version of this file under
   - the terms of any one of the MPL, the GPL or the LGPL.
   -
   - ***** END LICENSE BLOCK *****  -->

<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="text"></xsl:output>
  <xsl:template match="/elements">
    <xsl:text>package org.dcm4che.data;

public class StandardElementDictionary extends ElementDictionary {

    public static final ElementDictionary INSTANCE =
            new StandardElementDictionary();

    private StandardElementDictionary() {
        super(null, Tag.class);
    }

    @Override
    public String keywordOf(int tag) {
        return Keyword.valueOf(tag);
    }

    @Override
    public VR vrOf(int tag) {
        if ((tag &amp; 0x0000FFFF) == 0)
            return VR.UL;
        if ((tag &amp; 0x00010000) != 0)
            return ((tag &amp; 0x0000FF00) == 0) ? VR.LO : VR.UN;
        if ((tag &amp; 0xFFFFFF00) == Tag.SourceImageIDs)
            return VR.CS;
        int tmp = tag &amp; 0xFFE00000;
        if (tmp == 0x50000000 || tmp == 0x60000000)
            tag &amp;= 0xFFE0FFFF;
        else if ((tag &amp; 0xFF000000) == 0x7F000000 
                &amp;&amp; (tag &amp; 0xFFFF0000) != 0x7FE00000)
            tag &amp;= 0xFF00FFFF;
        switch (tag) {</xsl:text>
    <xsl:apply-templates select="element[starts-with(@vr,'AE')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'AS')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'AT')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'CS') and @keyword!='SourceImageIDs']"/>
    <xsl:apply-templates select="element[starts-with(@vr,'DA')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'DS')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'DT')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'FL')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'FD')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'IS')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'LO')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'LT')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'OB')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'OF')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'OW')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'PN')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'SH')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'SL')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'SQ')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'SS')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'ST')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'TM')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'UI')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'UL')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'US')]"/>
    <xsl:apply-templates select="element[starts-with(@vr,'UT')]"/>
    <xsl:apply-templates select="element[@keyword!='' and @vr='']"/>
<xsl:text>
        }
        return VR.UN;
    }
}
</xsl:text>
  </xsl:template>

  <xsl:template match="element">
    <xsl:if test="not(starts-with(@tag,'(0028,04x'))">
      <xsl:text>
        case Tag.</xsl:text>
      <xsl:value-of select="@keyword"/>
      <xsl:text>:</xsl:text>
    </xsl:if>
    <xsl:if test="position()=last()">
      <xsl:choose>
        <xsl:when test="@vr=''">
          <xsl:text>
            return null;</xsl:text>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>
            return VR.</xsl:text>
          <xsl:value-of select="substring(@vr,string-length(@vr)-1)"/>
          <xsl:text>;</xsl:text>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>