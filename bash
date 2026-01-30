# Source - https://stackoverflow.com/q/60408070
# Posted by The Lion King
# Retrieved 2026-01-05, License - CC BY-SA 4.0

#!/usr/bin/env bash

echo "Script initialized."

# Putting on a variable the address given as an argument:
BaseDirectory=${1}

echo ""
echo "Full address of the base directory: $BaseDirectory"
echo ""

# Finding (recursively) all the *.txt files from the directory this script is being executed:
echo "Text files to be analyzed are the following:"
find . -iname '*.txt' -exec echo "{}" \;
echo ""

for File in $BaseDirectory
do
        echo "File name: $File"
        NumberOfWords=(wc -w $File) #Counting the words present in the file
        echo "Number of words within this file: $NumberOfWords"
        echo ""
done

echo ""
echo "Script totally executed."
echo ""

read -p "Press [ENTER] to close this window."

