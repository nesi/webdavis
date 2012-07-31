#!/bin/sh -x

#
# Simple configure script for Davis. Must be run from davis/bin and as root.
#
# NOTES
# =====
#
# * To keep local config info between installs, put host-local.properties 
#   in davis/../etc/. This only needs to define server-host=localhost, zone-name, default-resource.
#   This script will create a link to that in the config directory.
#
# * If Davis runs behind Apache, make sure there is a soft link in /var/www/html to davis/webapps/images
#   so that Apache can serve Davis' image files.
#
# V1 Rowan McKenzie  22/10/09

cd=`basename $PWD`

if [ $cd != bin ] ; then
  echo This script must be run from the Davis bin directory
  exit 1
fi

if [ `whoami` != root ] ; then
  echo This script must be run as root
  exit 1
fi

# Set ownership of everything
cd ..
chown -R davis .
chgrp -R davis .

# Point to local config items in davis/../etc/. Davis will skip them if they aren't there.
cd webapps/root/WEB-INF/
ln -s ../../../../etc/host-local.properties davis-host.properties
#ln -s ../../../../etc/davis-site.properties .
