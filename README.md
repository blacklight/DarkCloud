# DarkCloud

DarkCloud is a software designed for file sharing over a distributed network
where the server nodes can be generic local/remote nodes which run the
application, or cloud services (TODO).

## Description

DarkCloud basically consists in three types of nodes:

* ***Server*** nodes. They are what basically store the information. Each server
  node can hold 0..n **fragments** of a file. In fact, each file stored on the
  network is splitted among several server nodes, so no node holds a whole piece
  of information, but basically all the server nodes alive in a certain moment
  get a piece of the information. Moreover, in order to increase the security of
  the network, each client node that wants to store a file on the network, first
  encrypts that file using a self-generated symmetric key, then encrypts that
  key using its own public key, saving it on its own database, and sends
  fragments of the encrypted file to the server nodes alive in that moment
  over SSL trusted channels.

* ***Client*** nodes. They receive requests for sharing a certain file and store
  it on the network by encrypting that file, splitting it and sending store
  requests to the server nodes. A client is uniquely identified by a pair of RSA
  keys and an SSL certificate used for the trusted connections. A client may
  also want to share a certain file with other clients. In that case:
  
  ** It generates the symmetric key for encrypting that file
  ** It splits the encrypted file in more pieces, depending on the number of
  server nodes alive in that moment. The sequence of server nodes that receive
  the fragments is random
  ** It sends the fragments of the file to the nodes in the specified random
  order
  ** It checks whether the client nodes to share that file with are alive. In
  that case, it encrypts the symmetric key of the file through that client's
  public key, so only the client should be able to decrypt it using its own
  private key. It also sends, encrypted in the same way, the sequence of servers
  to be queried in order to reconstruct the file, and the file's own checksum

* ***Terminal*** node. It consists of a Python script which is used by the
  end-user in order to interact with the network. It can only interact with
  client nodes, since the darknet-like topology of the network requires that the
  server nodes only communicate with known nodes. The terminal can be used to:

  ** Ping client nodes for checking whether they are available
  ** Store files on the network
  ** Retrieve files from the network

  All the requests are sent to client nodes, and from there, if they're allowed,
  forwarded to the server nodes

## Installation

Just check out the lateset software release via SVN:

	svn co https://darkcloud.googlecode.com/svn/trunk

### Requirements

* Java runtime environment >= 1.6.x, and both **java** and **keytool** available
  in **PATH**

* **sqlite3** client from command line available in **PATH**

## Configuration

In order to configure a new DarkCloud network:

* Move to the directory where you checked out the DarkCloud trunk

* Move to ***net*** directory

* In order to create a new node, use the script ***./createNode.sh***. You'll be
  asked a pair of questions: name for the node, type (client or server), and
  some information for generating the node's keys and certificate. Moreover, if
  other nodes have already been created, the script will automatically import
  their certificates and ask you if you want to mark them as trusted (you should
  do that, if you want to be able to communicate with a certain node)

* If you need to change any of the default values, go to the directory named
  like the node you just created and edit the file ***darkcloud.conf***. In
  particular, by default, all the known/trusted nodes on the network have
  127.0.0.1 as address. If you want to move some of them on another machine and
  communicate with them, replace the specific entry in the setting
  **darkcloud.nodes.host** with the IP address or hostname of that machine

* The known hosts in the networks have three configuration settings in
  ***darkcloud.conf***:

  ** **darkcloud.nodes.host**: here you set the IP addresses or hostnames of the
  nodes you want to communicate to

  ** **darkcloud.nodes.listenport**: here you specify on which ports those nodes
  will be listening

  ** **darkcloud.nodes.type**: here you specify the types for those nodes
  (client or server)

For instance, suppose you want to let a certain node communicate with two other
nodes:

	[addr=192.168.1.2, port=17100, type=server]
	[addr=192.168.1.3, port=18100, type=client]

In that case, the specific configuration lines in that node's
***darkcloud.conf*** file would be something like:

	darkcloud.nodes.host = 192.168.1.2, 192.168.1.3
	darkcloud.nodes.listenport = 17100, 18100
	darkcloud.nodes.type = server, client

# Usage

You can start the network (i.e. all the nodes in the **net** directory) by using
the script ***./startNet.sh*** in that directory.

You can stop the network (i.e. all the nodes in the **net** directory) by using
the script ***./stopNet.sh*** in that directory.

You can start or stop a specific node in the **net** directory by respectively
using the scripts ***./startNode.sh <nodeName>*** and ***./stopNode.sh
<nodeName>***.

All the activities for a certain node will be logged to
**./net/<nodename>/darkcloud.log**.

You can interact with the network by using the Python script ***client.py*** in
the trunk's directory.

For example:

* Ping a certain client node:
	$ python client.py -r PING -h <host addr> -p <listen port>

* Store the file <local file> to the network naming it <remote file>, using a
  specific client node as gateway:
	$ python client.py -r PUT -h <host addr> -p <listen port> -f <local file> -F <remote file>

* Retrieve the content of a specific file from the network:
	$ python client.py -r GET -h <host addr> -p <listen port> -F <remote file>

