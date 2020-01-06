#!/usr/bin/env filebot -script


def map = [:]
def history = getPersistentRenameLog()


getPersistentRenameLog().each { from, to ->
	// ignore history entires that were renamed in place
	if (from.dir == to.dir) {
		return
	}

	from.dir.listFiles().each{ f ->
		// ignore hidden files
		if (f.hidden || f in history) {
			return
		}

		map[f] = to.dir / f.name
	}
}


if (map) {
	rename(map: map)
}
