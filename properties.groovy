#!/usr/bin/env filebot -script


def f = ApplicationFolder.AppData.resolve('system.properties')
def p = new Properties()


if (_def.size() > 0) {
	if (f.exists()) {
		log.fine "Load user-defined System Properties"
		f.withReader('UTF-8'){
			log.fine "* Read $f"
			p.load(it)
		}
	}

	log.fine "Update user-defined System Properties"
	_def.each{ k, v ->
		if (v) {
			log.fine "* Set $k = $v"
			p.put(k, v)
		} else {
			log.fine "* Delete $k"
			p.remove(k)
		}
	}

	log.fine "Store user-defined System Properties"
	f.withWriter('UTF-8'){
		log.fine "* Write $f"
		p.store(it, 'FileBot System Properties')
	}
}


if (f.exists()) {
	// print configuration file
	println f.getText('UTF-8')
} else {
	// custom configuration file does not exist yet
	log.warning "No user-defined properties"
}
