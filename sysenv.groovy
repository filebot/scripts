#!/usr/bin/env filebot -script


log.fine '\n# Local Time #'
log.info "$now"

log.fine '\n# Process Tree #'
try {
	def t = []
	for (def p = ProcessHandle.current(); p != null; p = p.parent().orElse(null)) {
		t.push(p.info())
	}
	t.findResults{ p -> p.command().orElse(null) }.eachWithIndex{ p, i ->
		println(i == 0 ? p : '   ' * (i-1) + '└─ ' + p)
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
