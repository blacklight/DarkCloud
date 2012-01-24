#!/bin/bash

if (($# == 0)); then
	echo "Usage $0 <node to remove>"
	exit 1
fi

nodename=$1

if [ ! -d "$nodename" ]; then
	echo "$nodename: No such node"
	exit 1
fi

if [ ! -f "$nodename/darkcloud.conf" ]; then
	echo "$nodename: No such node"
	exit 1
fi

listenport=`egrep '^darkcloud.listenport\s*=\s*[0-9]+$\s*$' "$nodename/darkcloud.conf" | sed -r -e 's/^darkcloud\.listenport\s*=\s*([0-9]+)\s*$/\1/g'`

if [ -z "$listenport" ]; then
	echo "$nodename is an invalid node"
	exit 1
fi

nodetype=`egrep '^darkcloud.nodetype\s*=\s*[a-zA-Z]+$\s*$' "$nodename/darkcloud.conf" | sed -r -e 's/^darkcloud\.nodetype\s*=\s*([a-zA-Z]+)\s*$/\1/g'`

if [ -z "$nodetype" ]; then
	echo "$nodename is an invalid node"
	exit 1
fi

for dir in *; do
	remotenode=`basename "$dir"`

	if [ -d "$dir" -a -f "$dir/darkcloud.conf" -a "$remotenode" != "$nodename" ] ; then
		portlist=`egrep '^darkcloud.nodes.listenport\s*=\s*' "$dir/darkcloud.conf"`

		if [ ! -z "$portlist" ]; then
			portlist=`echo "$portlist" | sed -r -e 's/^darkcloud.nodes.listenport\s*=\s*(.*)\s*$/\1/g'`
			IFS=',' read -ra PORT <<< "$portlist"
			newports=
			portindex=-1
			index=-1
			
			for port in "${PORT[@]}"; do
				let index=index=1
				port=`echo "$port" | sed -r -e 's/^\s*([0-9]+)\s*$/\1/g'`
				
				if [ "$listenport" = "$port" ]; then
					let portindex=$index
				else
					newports="$port, "
				fi
			done

			portlist=`echo "$newports" | sed -r -e 's/^\s*(.*),\s*$/\1/g'`
		fi

		if [ $portindex -ge 0 ]; then
			nodelist=`egrep '^darkcloud.nodes.host\s*=\s*' "$dir/darkcloud.conf"`

			if [ ! -z "$nodelist" ]; then
				nodelist=`echo "$nodelist" | sed -r -e 's/^darkcloud.nodes.host\s*=\s*(.*)\s*$/\1/g'`
				IFS=',' read -ra NODES <<< "$nodelist"
				newnodes=
				hostindex=-1
				hostaddr=
			
				for node in "${NODES[@]}"; do
					let hostindex=hostindex+1

					if [ $hostindex -ne $portindex ]; then
						newnodes="$node, "
					else
						hostaddr=`echo "$node" | sed -r -e 's/^\s*(.*)\s*$/\1/g'`
					fi
				done

				nodelist=`echo "$newnodes" | sed -r -e 's/^\s*(.*),\s*$/\1/g'`
			fi
		fi

		if [ $portindex -ge 0 ]; then
			typelist=`egrep '^darkcloud.nodes.type\s*=\s*' "$dir/darkcloud.conf"`

			if [ ! -z "$typelist" ]; then
				typelist=`echo "$typelist" | sed -r -e 's/^darkcloud.nodes.type\s*=\s*(.*)\s*$/\1/g'`
				IFS=',' read -ra TYPES <<< "$typelist"
				newtypes=
				typeindex=-1
			
				for type in "${TYPES[@]}"; do
					let typeindex=typeindex+1

					if [ $typeindex -ne $portindex ]; then
						newtypes="$type, "
					fi
				done

				typelist=`echo "$newtypes" | sed -r -e 's/^\s*(.*),\s*$/\1/g'`
			fi
		fi

		if [ ! -z "$portlist" -a ! -z "$nodelist" -a ! -z "$typelist" ]; then
			sed -i "$dir/darkcloud.conf" \
				-e "s/^darkcloud.nodes.listenport\s*=\s*.*$/darkcloud.nodes.listenport = $portlist/g" \
				-e "s/^darkcloud.nodes.host\s*=\s*.*$/darkcloud.nodes.host = $nodelist/g" \
				-e "s/^darkcloud.nodes.type\s*=\s*.*$/darkcloud.nodes.type = $typelist/g"
		fi

		if [ -f "$dir/darkcloud.db" ]; then
			echo "DELETE FROM node WHERE addr='$hostaddr' AND port='$listenport'" | sqlite3 "$dir/darkcloud.db" > /dev/null 2>&1
		fi
	fi
done

rm -rf "$nodename"

