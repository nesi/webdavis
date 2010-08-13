#!/bin/bash

# Information : author / license information
# Description : description of script functionality
# Usage       : short usage

Usage(){
  printf "Usage: %s: [-i] [-d] [-h]\n" $(basename $0) >&2
  exit 0
}

Information() {
# note to the author: just type standard text b/w the "EOF" lines
cat <<EOF

        AUTHOR:  Dr Marco La Rosa, Marco.LaRosa@versi.edu.au
       COMPANY:  VeRSI : Victorian eResearch Strategic Initiative
     COPYRIGHT:  VeRSI, 2008
        AUTHOR:  Shunde Zhang, shunde.zhang@arcs.org.au
       COMPANY:  eResearch SA
       LICENSE:  This program is free software: you can redistribute it and/or modify
                 it under the terms of the GNU General Public License as published by
                 the Free Software Foundation, either version 3 of the License, or
                 (at your option) any later version.

                 This program is distributed in the hope that it will be useful,
                 but WITHOUT ANY WARRANTY; without even the implied warranty of
                 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
                 GNU General Public License for more details.

                 You should have received a copy of the GNU General Public License
                 along with this program.  If not, see <http://www.gnu.org/licenses/>.

EOF
  exit 0
}

Description() {
# note to the author: just type standard text b/w the "EOF" lines
cat <<EOF

This shell script will download and install the davis web dav client
version 6.1 to /opt/davis.

If you wish to change the install location and / or the version, 
edit this script and change the variables (DAVIS_TARBALL, INSTALL_LOCATION)
on lines 69 and 70 respectively to suit your needs.

scriptname: $(basename $0)
-----------

EOF
  exit 0
}

while getopts 'idh' OPTION
  do
    case $OPTION in
    i)
      Information
      ;;
    d)
      Description
      ;;
    h)
      Usage
      ;;
    esac
done

DAVIS_TARBALL=http://webdavis.googlecode.com/files/davis-0.6.1.tar.gz
DAVIS_TARBALL_BASENAME=$(basename $DAVIS_TARBALL)
DAVIS_TARBALL_BASENAME=$(basename $DAVIS_TARBALL_BASENAME .tar.gz)
INSTALL_LOCATION=/opt/davis
JETTY_HOME=${INSTALL_LOCATION}/current

if [ "$1" == "cleanup" ] ; then

  service davis stop
  rm -rf ${INSTALL_LOCATION}
  chkconfig --del davis
  rm -f /etc/init.d/davis
  rm -f /etc/sysconfig/jetty
  exit 0

fi

cat <<EOF

Installing into:  $INSTALL_LOCATION
Davis version  :  $DAVIS_TARBALL

EOF

# first - check that a hostcert has been installed - exit if no
if [ ! -f /etc/grid-security/hostcert.pem ] ; then

cat <<EOF

  ** Doesn't seem like a host certificate is installed.

  Expecting to find : /etc/grid-security/hostcert.pem

  Exiting now. Re-run this installer after you've installed
  a host certificate.

EOF

exit 0

fi

# download and unpack the tarball
[[ ! -d $INSTALL_LOCATION ]] && mkdir -p $INSTALL_LOCATION 
cd $INSTALL_LOCATION
wget -q ${DAVIS_TARBALL}
tar -zxf $(basename ${DAVIS_TARBALL})
ln -sf ${DAVIS_TARBALL_BASENAME} $JETTY_HOME

# remove the davis tarball
rm -f $(basename ${DAVIS_TARBALL})

# install the init script for the software
cp -f ${JETTY_HOME}/bin/jetty.sh /etc/init.d/davis
chmod +x /etc/init.d/davis
chkconfig --add davis
chkconfig davis on

# get the CA cert and export it into pkcs12 format for davis to use
mkdir -p /tmp/davis && cd /tmp/davis
cp -f /etc/grid-security/certificates/1e12d831.0 .

# create the required .p12 file - make sure to remember the password
cat /etc/grid-security/hostcert.pem 1e12d831.0 > cert-chain.txt
openssl pkcs12 -export -inkey /etc/grid-security/hostkey.pem -in cert-chain.txt -out jetty.pkcs12 

# keystore for java and place it in $JETTY_HOME/etc
java -classpath $JETTY_HOME/lib/jetty-util-6.1.12.jar:$JETTY_HOME/lib/jetty-6.1.12.jar org.mortbay.jetty.security.PKCS12Import jetty.pkcs12 keystore
OBFPW=$(java -classpath $JETTY_HOME/lib/jetty-util-6.1.12.jar:$JETTY_HOME/lib/jetty-6.1.12.jar org.mortbay.jetty.security.Password 'v3rs1' 2>&1 | grep ^OBF)

# put the keystore in $JETTY_HOME/etc
mv keystore $JETTY_HOME/etc/

# clean up the tmp space
cd /root/scriptlets
rm -rf /tmp/davis

# edit the jetty.xml file to include the new password
sed 's/OBF:1ktv1x0r1z0f1z0f1x1v1kqz/OBF:1m841lxb1y7z1m0v1m4a/g' $JETTY_HOME/etc/jetty.xml > $JETTY_HOME/etc/jetty.xml.tmp
mv -f $JETTY_HOME/etc/jetty.xml.tmp $JETTY_HOME/etc/jetty.xml 

# set up the main config file
echo "JETTY_HOME=$JETTY_HOME" > /etc/sysconfig/jetty

# and fix up the davis init script - seems it was written for debian
sed 's@/etc/default/jetty@/etc/sysconfig/jetty@g' /etc/init.d/davis > /etc/init.d/davis.tmp
mv -f /etc/init.d/davis.tmp /etc/init.d/davis
chmod +x /etc/init.d/davis

####
#### Jetty should now be installed
####  Now to configure it....
####

#if [ "$(hostname -d)" == "versi.unimelb.edu.au" ] ; then
#  CSERVER=srb.versi.unimelb.edu.au
#  CZONE=versi.melbourne
#  CRESOURCE=OUTREACH-MEL

#elif [ "$(hostname -d)" == "versi.monash.edu.au" ] ; then
#  CSERVER=srb.versi.monash.edu.au
#  CZONE=versi.monash
#  CRESOURCE=OUTREACH-MON

#elif [ "$(hostname -d)" == "versi.latrobe.edu.au" ] ; then
#  CSERVER=srb.versi.latrobe.edu.au
#  CZONE=versi.latrobe
#  CRESOURCE=OUTREACH-LAT
#fi

#if [ "$(hostname -s)" == "srb" ] ; then
#  CSERVER=localhost
#fi

CSERVER=
CZONE=
CRESOURCE=

cd $JETTY_HOME/webapps/root/WEB-INF/
cat <<EOF > web.xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app id="WebApp_ID" version="2.4" xmlns="http://java.sun.com/xml/ns/j2ee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd">
<display-name>Davis WebDAV-iRods/SRB Gateway - Version 0.5.0</display-name>
<filter>
  <filter-name>Compression</filter-name>
  <filter-class>webdavis.CompressionFilter</filter-class>
</filter>
<filter-mapping>
  <filter-name>Compression</filter-name>
<servlet-name>Davis</servlet-name>
</filter-mapping>
<servlet>
  <servlet-name>Davis</servlet-name>
  <servlet-class>webdavis.Davis</servlet-class>
    <init-param>
        <param-name>insecureBasic</param-name>
        <param-value>true</param-value>
    </init-param>
    <init-param>
        <param-name>webdavis.Log.threshold</param-name>
        <param-value>DEBUG</param-value>
    </init-param>
    <init-param>
	<description>
		server type, srb or irods
	</description>
	<param-name>server-type</param-name>
	<param-value>srb</param-value>
    </init-param>
    <init-param>
	<description>
		default idp name
	</description>
	<param-name>default-idp</param-name>
	<param-value>VeRSI OpenIDP</param-value>
    </init-param>
    <init-param>
	<description>server port of srb/irods</description>
	<param-name>server-port</param-name>
	<param-value>5544</param-value>
    </init-param>
    <init-param>
	<description>server name of srb/irods</description>
	<param-name>server-name</param-name>
	<param-value>$CSERVER</param-value>
    </init-param>
    <init-param>
	<description>zone name of srb/irods</description>
	<param-name>zone-name</param-name>
	<param-value>$CZONE</param-value>
    </init-param>
    <init-param>
	<description>default domain of user</description>
	<param-name>default-domain</param-name>
	<param-value>$CZONE</param-value>
    </init-param>
		<!-- 
		default resource of user in this order: 
			1. resource_name equals this value; 
			2. resource_name contains this value; 
			3. first resource in the list
		 -->
    <init-param>
	<description>default resource of user</description>
	<param-name>default-resource</param-name>
	<param-value>$CRESOURCE</param-value>
    </init-param>
    <init-param>
	<description>proxy host for slcs, set to empty if no proxy is needed</description>
	<param-name>proxy-host</param-name>
	<param-value></param-value>
    </init-param><!-- www-proxy.sapac.edu.au -->
    <init-param>
	<description>proxy port for slcs, set to empty if no proxy is needed</description>
	<param-name>proxy-port</param-name>
	<param-value></param-value>
    </init-param><!-- 8080 -->
    <init-param>
	<description>proxy username for slcs, set to empty if no proxy is needed</description>
	<param-name></param-name>
	<param-value></param-value>
    </init-param>
    <init-param>
	<description>proxy password for slcs, set to empty if no proxy is needed</description>
	<param-name></param-name>
	<param-value></param-value>
    </init-param>
    <init-param>
	<description>dojoroot, if it is deployed at root, put a "/" here, otherwise, leave it blank</description>
	<param-name>dojoroot</param-name>
	<param-value></param-value>
    </init-param>
</servlet>
<servlet-mapping>
<servlet-name>Davis</servlet-name>
<url-pattern>/*</url-pattern>
</servlet-mapping>
	<listener>
		<listener-class>webdavis.DavisListener</listener-class>
	</listener>
	        <session-config>
                <session-timeout>30</session-timeout>
        </session-config>
</web-app>
EOF


# start 'er up
service davis start

