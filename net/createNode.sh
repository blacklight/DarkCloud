#!/bin/bash

nodename=
nodetype=
listenport=
keystorepwd=
usedports=

echo -n > .tmp

for dir in *; do
	if [ -d "$dir" ]; then
		portline=`egrep '^darkcloud.listenport\s*=\s*[0-9]+\s*$' $dir/darkcloud.conf 2> /dev/null`

		if [ ! -z "$portline" ]; then
			port=`echo "$portline" | sed -r -e s/[^0-9]//g`
			echo -n "$port|" >> .tmp
		fi
	fi
done

usedports=`cat .tmp`
rm .tmp

while [ -z $nodename ]; do
	read -p "Node name [e.g. 'my_client']: " nodename

	if [ -d $nodename ]; then
		echo "That node name already exists, please choose another one"
		nodename=
	fi
done

while [ -z $nodetype ]; do
	read -p "Node type [client or server]: " nodetype

	if [ ! $nodetype = "client" -a ! $nodetype = "server" ]; then
		nodetype=
	fi
done

while [ -z $listenport ]; do
	read -p "Listen port [1024-65535]: " listenport

	if [ -z `echo "$listenport" | egrep '^[0-9]+$'` ]; then
		listenport=
	elif [ $listenport -lt 1024 -o $listenport -gt 65535 ]; then
		listenport=
	elif [ ! -z `echo "$usedports" | grep "$listenport|"` ]; then
		echo "This port is already used, please choose another"
		listenport=
	fi
done

while [ -z $keystorepwd ]; do
	stty -echo
	read -p "Keystore password: " keystorepwd
	stty echo
	echo
done

mkdir -p "$nodename"
cp ../util/darkcloud.conf "$nodename"
cd "$nodename"

echo -n > .hosts
echo -n > .ports
echo -n > .types

for dir in ../*; do
	if [ -d "$dir" -a ! "$dir" = "../$nodename" ]; then
		portline=`egrep '^darkcloud.listenport\s*=\s*[0-9]+\s*$' $dir/darkcloud.conf 2> /dev/null`

		if [ ! -z "$portline" ]; then
			port=`echo "$portline" | sed -r -e s/[^0-9]//g`
			echo -n "$port, " >> .ports
			echo -n '127.0.0.1, ' >> .hosts
		fi

		typeline=`egrep '^darkcloud.nodetype\s*=\s*[a-zA-Z]+\s*$' $dir/darkcloud.conf 2> /dev/null`

		if [ ! -z "$typeline" ]; then
			ntype=`echo "$typeline" | sed -r -e 's/^darkcloud.nodetype\s*=\s*([a-zA-Z]+)\s*$/\1/g'`
			echo -n "$ntype, " >> .types
		fi

		portlist=`egrep '^darkcloud.nodes.listenport\s*=\s*[0-9]+' $dir/darkcloud.conf 2> /dev/null`

		if [ ! -z "$portlist" ]; then
			if [ -z `echo "$portlist" | egrep "[^0-9]*$listenport[^0-9]*"` ]; then
				sed -i "$dir/darkcloud.conf" -r \
					-e "s/^(darkcloud.nodes.listenport\s*=\s*.*)$/\1, $listenport/g" \
					-e "s/^(darkcloud.nodes.host\s*=\s*.*)$/\1, 127.0.0.1/g" \
					-e "s/^(darkcloud.nodes.type\s*=\s*.*)$/\1, $nodetype/g"
			fi
		fi
	fi
done

if [ -f .hosts ]; then
	hosts=`cat .hosts | sed -r -e 's/^(.*),\s*$/, \1/g'`
fi

if [ -f .types ]; then
	types=`cat .types | sed -r -e 's/^(.*),\s*$/, \1/g'`
fi

if [ -f .ports ]; then
	ports=`cat .ports | sed -r -e 's/^(.*),\s*$/, \1/g'`
fi

rm -f .hosts
rm -f .ports
rm -f .types

sed -i darkcloud.conf \
	-e "s/^#darkcloud.nodetype\s*=\s*.*$/darkcloud.nodetype = $nodetype/" \
	-e "s/^#darkcloud.listenport\s*=\s*.*$/darkcloud.listenport = $listenport/" \
	-e "s/^#darkcloud.keystorepwd\s*=\s*.*$/darkcloud.keystorepwd = $keystorepwd/" \
	-e "s/^#darkcloud.nodes.host\s*=\s*.*$/darkcloud.nodes.host = 127.0.0.1$hosts/" \
	-e "s/^#darkcloud.nodes.listenport\s*=\s*.*$/darkcloud.nodes.listenport = $listenport$ports/" \
	-e "s/^#darkcloud.nodes.type\s*=\s*.*$/darkcloud.nodes.type = $nodetype$types/"

chmod 0600 darkcloud.conf

keytool -keystore darkcloud.keystore -genkey -keyalg RSA -alias "$nodename" -storepass "$keystorepwd"
echo "Keystore file generated in darkcloud.keystore"
keytool -v -exportcert -alias "$nodename" -keystore darkcloud.keystore -file "$nodename.crt" -storepass "$keystorepwd"
echo "Certificate file generated in $nodename.crt"

for dir in ../*; do
	remotenode=`basename "$dir"`

	if [ -d "$dir" -a "$dir" != "../$nodename" -a -f "$dir/$remotenode.crt" ]; then
		echo "Importing $remotenode SSL certificate..."
		keytool -v -import -trustcacerts -alias "$remotenode" -file "$dir/$remotenode.crt" -keystore darkcloud.keystore -storepass "$keystorepwd"

		echo "Marking my certificate as trusted for $remotenode..."
		nodepwd=

		if [ -f "$dir/darkcloud.conf" ]; then
			if [ ! -z "`egrep '^darkcloud.keystorepwd\s*=\s*' \"$dir/darkcloud.conf\"`" ]; then
				nodepwd=`egrep '^darkcloud.keystorepwd\s*=\s*' "$dir/darkcloud.conf" | sed -r -e 's/^darkcloud.keystorepwd\s*=\s*(.*)\s*$/\1/g'`
			fi
		fi

		if [ -z "$nodepwd" ]; then
			keytool -v -import -trustcacerts -alias "$nodename" -file "$nodename.crt" -keystore "$dir/darkcloud.keystore"
		else
			keytool -v -import -trustcacerts -alias "$nodename" -file "$nodename.crt" -keystore "$dir/darkcloud.keystore" -storepass "$nodepwd"
		fi
	fi
done

ln -sf ../../darkcloud-trunk.jar ./darkcloud-trunk.jar
ln -sf ../../util/log4j.properties ./log4j.properties

