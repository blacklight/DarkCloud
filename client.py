#!/usr/bin/python

import base64
import getopt
import hashlib
import socket
import ssl
import sys

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
	print ("Usage: " + sys.argv[0] + " -h <host> -p <port> -r <request string> [-f <local file>] [-F <remote file>]")
	print ("Valid requests: ping, put, get")

def main():
	config = {}
	optlist, args = getopt.getopt(sys.argv[1:], "f:F:h:p:r:", ["localfile=", "remotefile=", "host=", "port=", "request="])

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

	if not 'host' in config or not 'port' in config or not 'request' in config:
		usage()
		return 1

	if config['request'].lower() == 'ping':
		req = Request(reqtype="PING")
	elif config['request'].lower() == 'put':
		if not 'localfile' in config:
			print "PUT method used, but no local file was specified"
			usage()
			return 1

		if not 'remotefile' in config:
			print "PUT method used, but no remote file name was specified"
			usage()
			return 1

		f = open(config['localfile'], 'r')
		filecontent = f.read()
		f.close()

		req = Request(reqtype="PUT",
			fields={
				('file', 'encoding'): 'base64',
				('file', 'name'): config['remotefile'],
				('file', 'content'): filecontent,
				('file', 'checksum'): hashlib.md5(filecontent).hexdigest()
			}
		)
	elif config['request'].lower() == 'get':
		if not 'remotefile' in config:
			print "GET method used, but no remote file name was specified"
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
	print fs.read()
	fs.close()
	sock.close()

if __name__ == "__main__":
     main()

