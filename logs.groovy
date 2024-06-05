#!/usr/bin/env -S filebot -script


def folder = ApplicationFolder.AppData.get()
def logFiles = folder.getFiles{ it.extension == 'log' }.toSorted{ it.lastModified() }


if (logFiles.size() == 0) {
	println "# $folder"
	println "0 log files"
}


def grep = any{ ~_args.query }{ null }


logFiles.each{ f ->
	println "# $f"
	f.eachLine('UTF-8') { line ->
		if (grep == null || line =~ grep) {
			println line
		}		
	}
	println ""
}
