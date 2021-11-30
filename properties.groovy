#!/usr/bin/env filebot -script


def f = ApplicationFolder.AppData.resolve('system.properties')
def p = new Properties()


if (_def.size() > 0) {
	if (f.exists()) {
		log.fine "Load user-defined System Properties"
		f.withReader('UTF-8'){
			log.finest "* Read $f"
			p.load(it)
		}
	}

	log.fine "Update user-defined System Properties"
	_def.each{ k, v ->
		if (v) {
			log.finest "* Set $k = $v"
			p.put(k, v)
		} else {
			log.finest "* Delete $k"
			p.remove(k)
		}
	}

	log.fine "Store user-defined System Properties"
	f.withWriter('UTF-8'){
		log.finest "* Write $f"
		p.store(it, 'FileBot System Properties')
	}
}


println f.getText('UTF-8')
