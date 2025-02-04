#!/usr/bin/env -S filebot -script

args.eachMediaFolder{ f ->
	if (f.disk) {
		// process a disk folder as a single unit
		help "Disk Folder: $f"
		rename(file: f)
	} else {
		// process a set of files as usual
		help "Media Folder: $f"
		rename(folder: f)
	}
	// store history
	commit()
}
