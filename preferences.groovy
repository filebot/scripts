#!/usr/bin/env filebot -script


def prefs = java.util.prefs.Preferences.userRoot()


def printPreferences = {
	log.fine "Print $prefs"
	def buffer = new ByteArrayOutputStream()
	prefs.exportSubtree(buffer)
	println buffer.toString('UTF-8')
}


def importPreferences = { f ->
	log.fine "Import $prefs from $f"
	prefs.importPreferences(f.newInputStream())
}


def exportPreferences = { f ->
	log.fine "Export $prefs to $f"
	prefs.exportSubtree(f.newOutputStream())
}


def clearPreferences = {
	log.fine "Clear $prefs"
	prefs.childrenNames().each{ prefs.node(it).removeNode() }
	prefs.clear();
}




if (args) {
	args.each{ f -> importPreferences(f) }
}

if (_args.output) {
	def f = new File(_args.output, 'java.prefs.xml').getCanonicalFile()
	exportPreferences(f)
}

if (_args.action == 'clear') {
	clearPreferences()
}

printPreferences()
