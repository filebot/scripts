#!/usr/bin/env -S filebot -script

/**
 * Haven - Decentralized Media Archival for FileBot
 * 
 * This script uploads media files to Haven's decentralized storage network
 * (Filecoin/IPFS) with optional encryption, VLM analysis, and blockchain sync.
 * 
 * Usage:
 *   filebot -script haven.groovy /path/to/media [options]
 * 
 * Options:
 *   --def haven=true              Enable Haven upload
 *   --def havenEncrypt=true       Enable Lit Protocol encryption
 *   --def havenVLM=false          Disable VLM analysis
 *   --def havenArkiv=false        Disable Arkiv blockchain sync
 *   --def havenDataset=123        Use specific Filecoin dataset ID
 *   --def havenConfig=/path       Custom Haven config file
 *   --def havenFailOnError=true   Fail if upload fails
 *   --def havenQueue=true         Queue for background processing
 *   --def processQueue=true       Process pending queue instead of uploading
 *   --def title="Custom Title"    Override video title
 *   --def creator="@handle"       Override creator handle
 *   --def source="url"            Override source URL
 * 
 * Examples:
 *   # Basic upload
 *   filebot -script haven.groovy /movies/movie.mkv --def haven=true
 * 
 *   # Upload with encryption
 *   filebot -script haven.groovy /movies/movie.mkv \
 *     --def haven=true havenEncrypt=true
 * 
 *   # Upload TV episode
 *   filebot -script haven.groovy /tv/show.s01e01.mkv \
 *     --def title="Show Name" creator="@network"
 * 
 *   # Process queue
 *   filebot -script haven.groovy --def processQueue=true
 */

// Log script execution
log.fine("Haven integration script starting at [$now]")

// Include Haven library
include('lib/haven')

// Include web utilities for notifications
if (pushover || pushbullet || discord) { 
    include('lib/web') 
}

// Parse script parameters
def havenEnabled = tryQuietly { haven.toBoolean() }
def processQueue = tryQuietly { processQueue.toBoolean() }
def customTitle = tryQuietly { title.toString() }
def customCreator = tryQuietly { creator.toString() }
def customSource = tryQuietly { source.toString() }

// Create options container
def options = createHavenOptions(binding.variables)

// Handle queue processing mode
if (processQueue) {
    log.info "Processing Haven upload queue..."
    def stats = processHavenQueue(options.toMap())
    log.info "Queue processing complete: ${stats.succeeded}/${stats.processed} succeeded"
    return
}

// Validate Haven CLI is available
if (!checkHavenCLI()) {
    if (options.failOnError) {
        die "Haven CLI not found. Please install and configure Haven CLI.", ExitCode.FAILURE
    } else {
        log.warning "Haven CLI not found. Skipping Haven integration."
        return
    }
}

// Validate input files
if (args.size() == 0) {
    die "No input files specified. Usage: filebot -script haven.groovy /path/to/media [options]", ExitCode.FAILURE
}

// Process each input file
def results = []

def files = args.collectMany { 
    it.isDirectory() ? it.listFiles().toList() : [it] 
}.findAll { 
    it.isFile() && (it.isVideo() || it.isDisk()) 
}

log.info "Processing ${files.size()} files for Haven archival"

files.each { file ->
    log.info "Processing for Haven: $file"
    
    // Extract metadata from FileBot
    def metadata = extractHavenMetadata(file)
    
    // Override with custom parameters if provided
    if (customTitle) metadata.title = customTitle
    if (customCreator) metadata.creator = customCreator
    if (customSource) metadata.source = customSource
    
    log.fine "Metadata: title=${metadata.title}, creator=${metadata.creator}, type=${metadata.type}"
    
    // Queue or upload directly
    if (options.queue) {
        queueForHavenUpload(file, metadata, options.toMap())
        results << [file: file.name, status: 'queued']
        log.info "Queued for Haven upload: $file.name"
    } else {
        def result = uploadToHaven(file, options.toMap() + metadata, options.maxRetries)
        
        if (result.success) {
            log.info "Haven upload successful: $file.name"
            if (result.result?.cid) {
                log.info "Filecoin CID: ${result.result.cid}"
            }
            results << [file: file.name, status: 'success', cid: result.result?.cid]
        } else {
            log.warning "Haven upload failed for $file.name: ${result.error}"
            results << [file: file.name, status: 'failed', error: result.error]
            if (options.failOnError) {
                die "Haven upload failed: ${result.error}", ExitCode.FAILURE
            }
        }
    }
}

// Summary
log.info "Haven processing complete: ${results.count { it.status == 'success' }} uploaded, ${results.count { it.status == 'queued' }} queued, ${results.count { it.status == 'failed' }} failed"

// Send notifications if configured
if (pushover && results) tryLogCatch {
    log.fine 'Sending Pushover notification'
    def message = formatHavenResults(results)
    Pushover(pushover[0], pushover[1]).send("Haven Upload Complete", message)
}

if (discord && results) tryLogCatch {
    log.fine 'Sending Discord notification'
    def lines = formatHavenResults(results).readLines()
    def title = lines[0]
    def details = lines.size() > 1 ? lines[1..-1].join('\n') : ''
    
    def embed = [[
        title: title,
        description: details,
        color: results.any { it.status == 'failed' } ? 0xff0000 : 0x00ff00
    ]]
    
    Discord(discord).postEmbed(embed)
}

// Return results for potential further processing
return results
