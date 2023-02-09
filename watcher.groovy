#!/usr/bin/env filebot -script


// watch given input folder
args*.watchFolder{ changes ->
	// call amc script on newly added files
	executeScript('amc', changes)
}


println "Press ENTER to quit..."
console.readLine()
