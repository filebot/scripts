#!/usr/bin/env filebot -script


def f = ApplicationFolder.AppData.resolve('system.properties')
def p = new Properties()


if (_def.size() > 0) {
	if (f.exists()) {
		log.fine "Load user-defined System Properties"
		f.withInputStream{
			log.finest "* Read $f"
			p.load(it)
		}
	}

	log.fine "Update user-defined System Properties"
	_def.each{ k, v ->
		log.finest "* Set $k = $v"
		p.put(k, v)
	}

	log.fine "Store user-defined System Properties"
	f.withOutputStream{
		log.finest "* Write $f"
		p.store(it, 'FileBot System Properties')
	}
}


println f.text
