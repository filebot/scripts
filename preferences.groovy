#!/usr/bin/env -S filebot -script


def prefs = UserData.root()

// import preferences
args.each{ f ->
	log.fine "Import [$prefs] from [$f]"
	prefs.restore(f.bytes)
}

// export preferences
if (_args.output) {
	def f = new File(_args.output, 'preferences.xml')
	log.fine "Export [$prefs] to [$f]"
	prefs.export().saveAs(f)
}

// clear preferences
if (_args.action == 'clear') {
	log.fine "Clear [$prefs]"
	prefs.clear()
}

// print preferences to console output
println prefs.export().getText()
