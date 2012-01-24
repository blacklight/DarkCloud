#!/bin/bash

gen_keystore=0

if [ ! -f darkcloud.keystore ]
then
	gen_keystore=1
else
	echo -n "The keystore file darkcloud.keystore already exists. Would you like to overwrite it? (y/N) "
	read answer

	if [ "$answer" = 'y' -o "$answer" = 'Y' ]
	then
		gen_keystore=1
		rm darkcloud.keystore
	else
		gen_keystore=0
	fi
fi

if [ $gen_keystore -eq 0 ]
then
	echo "Abort"
	exit 1
fi

echo -n "Keystore alias name [e.g. 'MyClientNode']: "
read keyalias

keytool -keystore darkcloud.keystore -genkey -keyalg RSA -alias $keyalias
echo "Keystore file generated in darkcloud.keystore"

keytool -v -exportcert -alias $keyalias -keystore darkcloud.keystore -file $keyalias.crt
echo "Certificate file generated in $keyalias.crt"

