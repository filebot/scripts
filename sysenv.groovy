#!/usr/bin/env filebot -script


log.fine '\n# Local Time #'
log.info "$now"

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
