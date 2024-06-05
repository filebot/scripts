#!/usr/bin/env -S filebot -script


// print input folders
args.each{ folder -> log.fine "Watch $folder" }


// watch input folders
args*.watchFolder{ changes ->
	// call amc script on newly added files
	executeScript('amc', changes)
	commit()
}


// keep running indefinitely
println "Press ENTER to quit..."
console.readLine()
