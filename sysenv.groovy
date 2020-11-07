#!/usr/bin/env filebot -script


log.fine '\n# Environment Variables #'
_environment.entrySet().toSorted{ it.key }.each{ k, v ->
	log.info "$k: $v"
}

log.fine '\n# Java System Properties #'
_system.entrySet().toSorted{ it.key }.each{ k, v ->
	log.info "$k: $v"
}

log.fine '\n# Arguments #'
_args.argumentArray.eachWithIndex{ a, i ->
	log.info "args[$i] = $a"
}
