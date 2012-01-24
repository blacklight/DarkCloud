#!/bin/bash

l=0

find . -name '*.java' -exec wc -l '{}' \; | awk '{print $1}' | while read line
do
	let l=l+line
	echo $l > lines.txt
done

cat lines.txt
rm -f lines.txt

