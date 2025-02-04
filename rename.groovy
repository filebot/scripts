#!/usr/bin/env -S filebot -script

args.eachMediaFolder{ f ->
	if (f.disk) {
		// process a disk folder as a single unit
		rename(file: f)
	} else {
		// process a set of files as usual
		rename(folder: f)
	}
	// store history
	commit()
}
