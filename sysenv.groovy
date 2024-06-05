#!/usr/bin/env -S filebot -script


log.fine '\n# Local Time #'
log.info "$now"


log.fine '\n# Process Tree #'
try {
	def t = []
	for (def p = ProcessHandle.current(); p != null; p = p.parent().orElse(null)) {
		t.push p.info()
	}
	t.findResults{ p -> p.command().orElse(null) }.eachWithIndex{ p, i ->
		log.info "${i == 0 ? p : '   ' * (i-1) + '└─ ' + p}"
	}
} catch(Throwable e) {
	log.warning "$e"
}


log.fine '\n# Environment Variables #'
_environment.toSorted{ it.key }.each{ k, v ->
	log.info "$k: $v"
}


log.fine '\n# Java System Properties #'
_system.toSorted{ it.key }.each{ k, v ->
	log.info "$k: $v"
}


log.fine '\n# Arguments #'
_args.argumentArray.eachWithIndex{ a, i ->
	log.info "args[$i] = $a"
}


// print stat for each valid file argument as well (Unix only)
if (File.separator == '/') {
	args.each{ f ->
		if (f.isFile()) {
			log.fine "\n# ${f} #"
			['unix:dev', 'unix:ino', 'unix:nlink', 'unix:lastModifiedTime', 'unix:uid', 'unix:gid', 'unix:owner', 'unix:group', 'unix:permissions'].each{ k ->
				try {
					log.info "$k = ${f.getAttribute(k)}"
				} catch(Throwable e) {
					log.info "$k = $e"
				}
			}
		}
	}
}
