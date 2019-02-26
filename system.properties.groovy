#!/usr/bin/env filebot -script


def f = ApplicationFolder.AppData.resolve('system.properties')
def p = new Properties()


if (f.exists()) {
	log.fine "Read user-defined System Properties"
	f.withInputStream{
		log.finest "* Read $f"
		p.load(it)
	}
}


if (_def.size() > 0) {
	log.fine "Update user-defined System Properties"
	_def.each{ k, v ->
		log.finest "* Set $k = $v"
		p.put(k, v)
	}

	log.fine "Persist user-defined System Properties"
	f.withOutputStream{
		log.finest "* Write $f"
		p.store(it, 'FileBot System Properties')
	}
}


println f.text
