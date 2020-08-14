#!/usr/bin/env filebot -script


println '# Environment Variables #'
_environment.entrySet().toSorted{ it.key }.each{
	println "$it.key: $it.value"
}
println '\n'

println '# Java System Properties #'
_system.entrySet().toSorted{ it.key }.each{
	println "$it.key: $it.value"
}
println '\n'

println '# Arguments #'
_args.argumentArray.eachWithIndex{ a, i ->
	println "args[$i] = $a"
}
println '\n'
