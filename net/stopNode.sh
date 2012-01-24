#!/bin/bash

if (($# == 0)); then
	echo "Usage: $0 <node to be started>"
	exit 1
fi

node=$1

if [ ! -d "$node" ]; then
	echo "$node is not a valid node"
	exit 1
fi

if [ ! -d .run ]; then
	echo "No node seems to be currently running"
	exit 1
fi

if [ ! -f ".run/$node.pid" ]; then
	echo "Node $node seems not to be running"
	exit 1
fi

pid=`cat ".run/$node.pid"`

if [ -z "$pid" ]; then
	echo "Error while stopping node $node, please stop it manually"
	exit 1
fi

kill $pid
rm ".run/$node.pid"

if [ `ls -la .run | wc -l` == 1 ]; then
	rmdir .run
fi

echo "Node $node has been stopped"

