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

if [ -f ".run/$node.pid" ]; then
	echo "$node seems to be already running"
	exit 1
fi

cd "$node"

if [ ! -f "darkcloud-trunk.jar" -o ! -f "darkcloud.conf" ]; then
	echo "$node is not a valid node"
	exit 1
fi

java -jar "darkcloud-trunk.jar" > /dev/null 2>&1 &
pid=$!
sleep 1
cd ..
mkdir -p .run
echo $pid > ".run/$node.pid"

echo "Node $node started, activity will be logged in $node/darkcloud.log"

