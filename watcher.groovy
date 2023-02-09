#!/usr/bin/env filebot -script


// watch folder and execute amc script on newly added files
args[0].watchFolder{ changes ->
	executeScript('amc', changes)
}


println "Press ENTER to quit..."
console.readLine()
