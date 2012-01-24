#!/bin/bash

for dir in *
do
	if [ -d "$dir" ]
	then
		./stopNode.sh "$dir"
	fi
done

