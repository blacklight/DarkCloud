#!/bin/bash

if (($# < 3))
then
	echo "Usage: $0 <certificate file> <keystore file> <alias>"
	exit 1
fi

keytool -v -import -trustcacerts -alias $3 -file $1 -keystore $2

