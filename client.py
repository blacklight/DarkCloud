#!/usr/bin/python

import base64
import getopt
import hashlib
import socket
import ssl
import sys
import xml.dom.minidom

class Request:
	def __init__ (self, reqtype, content=None, fields=None, seqnum=1):
		self.reqtype = reqtype
		self.content = content
		self.fields = fields
		self.seqnum = seqnum

	def toString(self):
		s = ('<?xml version="1.0" encoding="UTF-8"?>' + "\n\n" + \
			'<request seq="%s" type="%s">' + "\n") % (int(self.seqnum), self.reqtype)

		if self.fields:
			fieldstrings = {}
			fieldcontents = {}

			for field, attr in self.fields:
				content = None

				if not field in fieldstrings:
					fieldstrings[field] = "\t<" + field

				if attr and attr != "":
					if attr.lower() == 'content':
						fieldcontents[field] = self.fields[(field, attr)]
					else:
						fieldstrings[field] += ' %s="%s"' % (attr, self.fields[(field, attr)])

			for field in fieldstrings.keys():
				fieldstrings[field] += ">"

				if field in fieldcontents:
					fieldstrings[field] += "<![CDATA[%s]]>" % (base64.b64encode(fieldcontents[field]))

				fieldstrings[field] += "</%s>\n" % (field)
				s += fieldstrings[field]

		if self.content:
			s += "\t<content><![CDATA[%s]]></content>\n" % (base64.b64encode(self.content))

		s += '</request>' + "\n"
		s = "%d\n%s" % (len(s), s)
		return s

def usage():
	print ("Usage: " + sys.argv[0] + " -h <host> -p <port> -r <request string> [-f <local file>] [-F <remote file>] [-c <client>]")
	print ("Valid requests: ping, put, get, share")

def main():
	config = {}
	optlist, args = getopt.getopt(sys.argv[1:], "f:F:h:p:r:H:P:", ["localfile=", "remotefile=", "host=", "port=", "request=", "secondhost=", "secondport="])

	for opt, arg in optlist:
		if opt in ('-h', '--host'):
			config['host'] = arg
		elif opt in ('-p', '--port'):
			try:
				config['port'] = int(arg)
			except:
				usage()
				return 1

			if config['port'] < 1 or config['port'] > 65535:
				usage()
				return 1
		elif opt in ('-r', '--request'):
			config['request'] = arg
		elif opt in ('-f', '--localfile'):
			config['localfile'] = arg
		elif opt in ('-F', '--remotefile'):
			config['remotefile'] = arg
		elif opt in ('-H', '--secondhost'):
			config['secondhost'] = arg
		elif opt in ('-P', '--secondport'):
			config['secondport'] = arg
				

	if not 'host' in config or not 'port' in config or not 'request' in config:
		usage()
		return 1

	if config['request'].lower() == 'ping':
		req = Request(reqtype="PING")
	elif config['request'].lower() == 'share':
		if not 'secondhost' in config:
			print("SHARE method used, but no ip for the second client to share the information was specified")
			usage()
			return 1

		if not 'secondport' in config:
			print("SHARE method used, but no port for the second client to share the information was specified")
			usage()
			return 1

		if not 'remotefile' in config:
			print("SHARE method used, but no remote file to share was specified")
			usage()
			return 1

		req = Request(reqtype="SHARE",
			fields={
				('sharing', 'secondhost'): config['secondhost'],
				('sharing', 'secondport'): config['secondport'],
				('sharing', 'remotefile'): config['remotefile']
			}
		)
	elif config['request'].lower() == 'put':
		if not 'localfile' in config:
			print ("PUT method used, but no local file was specified")
			usage()
			return 1

		if not 'remotefile' in config:
			print ("PUT method used, but no remote file name was specified")
			usage()
			return 1

		f = open(config['localfile'], 'r')
		filecontent = f.read()
		f.close()

		req = Request(reqtype="PUT",
			fields={
				('file', 'encoding'): 'base64',
				('file', 'name'): config['localfile'],
				('file', 'content'): filecontent,
				('file', 'checksum'): hashlib.md5(filecontent).hexdigest()
			}
		)
	elif config['request'].lower() == 'get':
		if not 'remotefile' in config:
			print ("GET method used, but no remote file name was specified")
			usage()
			return 1

		req = Request(reqtype="GET",
			fields={
				('file', 'name'): config['remotefile'],
			}
		)

	sock = ssl.wrap_socket(socket.socket(socket.AF_INET, socket.SOCK_STREAM), ssl_version=ssl.PROTOCOL_SSLv3)
	sock.connect((config['host'], config['port']))
	sock.write(req.toString())

	fs = sock.makefile()
	fs.readline()
	fs.readline()
	resp = fs.read()
	fs.close()
	sock.close()

	document = xml.dom.minidom.parseString(resp)

	if len(document.getElementsByTagName("response")) == 0:
		sys.stderr.write ("The node sent an invalid response")
		return 1

	response = document.getElementsByTagName("response")[0]
	resptype = response.getAttribute("type")

	if not resptype:
		sys.stderr.write ("The node sent an invalid response")
		return 1

	if resptype.lower() == 'error':
		sys.stderr.write (document.getElementsByTagName("content")[0].firstChild.nodeValue)
		return 1

	if resptype.lower() == 'ack':
		if config['request'].lower() == 'ping':
			print ("The node at %s:%d is alive" % (config['host'], config['port']))
		elif config['request'].lower() == 'share':
			print ("The file %s has been successfully shared with %s:%d" % (config['remotefile'], config['secondhost'], config['secondport']))
		elif config['request'].lower() == 'put':
			print ("The file %s has been successfully saved on the network as %s" % (config['localfile'], config['remotefile']))
		elif config['request'].lower() == 'get':
			filetag = document.getElementsByTagName("file")[0]
			sys.stdout.write (base64.b64decode(filetag.firstChild.wholeText))

if __name__ == "__main__":
     main()

