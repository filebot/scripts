#!/usr/bin/env filebot -script


def folder = ApplicationFolder.AppData.get()
def logFiles = folder.getFiles{ it.extension == 'log' }.toSorted{ it.lastModified() }


if (logFiles.size() == 0) {
	println "# $folder"
	println "0 log files"
}


logFiles.each{
	println "# $it"
	it.eachLine('UTF-8') { println it }
	println ""
}
