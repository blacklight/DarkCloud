#!/bin/bash

for dir in *
do
	if [ -d "$dir" ]
	then
		./startNode.sh "$dir"
	fi
done

