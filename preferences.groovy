// filebot -script fn:preferences


def prefs = java.util.prefs.Preferences.userRoot()


def printPreferences = {
	log.info "Print $prefs"
	def buffer = new ByteArrayOutputStream()
	prefs.exportSubtree(buffer)
	println buffer.toString('UTF-8')
}


def importPreferences = { f ->
	log.info "Import $prefs from $f"
	prefs.importPreferences(f.newInputStream())
}


def exportPreferences = { f ->
	log.info "Export $prefs to $f"
	prefs.exportSubtree(f.newOutputStream())
}


def clearPreferences = {
	log.info "Clear $prefs"
	prefs.childrenNames().each{ prefs.node(it).removeNode() }
	prefs.clear();
}




if (args) {
	args.each{ f-> importPreferences(f) }
} 

if (_args.output) {
	def f = new File(_args.output, System.getProperty('user.name') + '.prefs.xml').getCanonicalFile()
	exportPreferences(f)
}

if (_args.action == 'clear') {
	clearPreferences()
}

printPreferences()
