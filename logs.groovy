#!/usr/bin/env filebot -script


def folder = ApplicationFolder.AppData.get()
def logFiles = folder.getFiles{ it.extension == 'log' }


if (logFiles.size() == 0) {
	log.finest "# $folder"
	log.warning "0 log files"
}


logFiles.each{ f ->
	println ""
	log.finest "# $f [Last-Modified: ${new Date(f.lastModified())}]"
	f.eachLine('UTF-8') { s ->
		println s
	}
	println ""
}
