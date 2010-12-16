#!/usr/bin/python

import httplib
import mimetypes
import base64
import sys
import string

def get_content_type(filename):
  return mimetypes.guess_type(filename)[0] or 'application/octet-stream'

def encode_multipart_formdata(subjectfile):
  f = open(subjectfile, 'r')
  subjectdata = f.read()
  f.close()
  #
  BOUNDARY = '--------------ThIs_Is_tHe_bouNdaRY_$'
  CRLF = '\r\n'
  L = []
  L.append('--' + BOUNDARY)
  L.append('Content-Disposition: form-data; name="del-list"; filename="%s"' % subjectfile)
  L.append('Content-Type: %s' % get_content_type(subjectfile))
  L.append('')
  L.append(subjectdata)
  #
  L.append('--' + BOUNDARY + '--')
  L.append('')
  body = CRLF.join(L)
  content_type = 'multipart/form-data; boundary=%s' % BOUNDARY
  return content_type, body

def submit(username, password, subjectfile):

  # "Basic" authentication encodes userid:password in base64. Note
  # that base64.encodestring adds some extra newlines/carriage-returns
  # to the end of the result. string.strip is a simple way to remove
  # these characters.

  auth = 'Basic ' + string.strip(base64.encodestring(username + ':' + password))
  content_type, body = encode_multipart_formdata(subjectfile)

#  conn = httplib.HTTPSConnection("arcs-df.vpac.org")
  conn = httplib.HTTPSConnection("srb.ac3.edu.au")
  headers = {
      'Authorization': auth,
      'User-Agent': 'Linux',
      'Content-Type': content_type
      }
  print body
#  conn.request('POST','/ARCSDEVBACKEND/home/public/AATest?method=upload', body, headers)
  conn.request('PUT','/ARCSDEVBACKEND/home/public/AATest/'+subjectfile, body, headers)
  response = conn.getresponse()
  print response.status, response.reason
  res = response.read()
  print res

def main():
  if len(sys.argv) < 4:
    print "Syntax: "+sys.argv[0]+" myproxy/<username> <password> <name of file to upload>"
    return 2
  return submit(sys.argv[1],sys.argv[2],sys.argv[3])

if __name__ == "__main__": sys.exit(main())
