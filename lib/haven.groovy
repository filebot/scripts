/****************************************************************************
 * Haven CLI Integration
 *        Decentralized video archival to Filecoin/IPFS via Haven
 ****************************************************************************/

/**
 * Check if Haven CLI is installed and accessible.
 * @return true if Haven CLI is available
 */
def checkHavenCLI() {
    try {
        def process = ["haven", "--version"].execute()
        process.waitFor()
        if (process.exitValue() == 0) {
            def version = process.text.trim()
            log.fine "Haven CLI detected: $version"
            return true
        }
    } catch (e) {
        log.warning "Haven CLI not found in PATH: $e"
    }
    return false
}

/**
 * Build Haven CLI command for file upload.
 * Maps to: haven upload file <path> [options]
 * 
 * @param file The file to upload
 * @param options Map of upload options:
 *   - encrypt: Boolean - Enable Lit Protocol encryption
 *   - vlm: Boolean - Enable VLM analysis (default: true)
 *   - arkiv: Boolean - Enable Arkiv blockchain sync (default: true)
 *   - dataset: Integer - Filecoin dataset ID
 *   - config: File - Custom config file path
 *   - title: String - Video title
 *   - creator: String - Creator handle
 *   - source: String - Original source URL
 * @return List of command arguments
 */
def buildHavenCommand(file, options = [:]) {
    def cmd = ["haven", "upload", "file", file.path]
    
    // Boolean flags
    if (options.encrypt) cmd << "--encrypt"
    if (options.vlm == false) cmd << "--no-vlm"
    if (options.arkiv == false) cmd << "--no-arkiv"
    
    // Optional parameters with values
    if (options.dataset) {
        cmd << "--dataset" << options.dataset.toString()
    }
    if (options.config) {
        cmd << "--config" << options.config.toString()
    }
    if (options.title) {
        cmd << "--title" << options.title
    }
    if (options.creator) {
        cmd << "--creator" << options.creator
    }
    if (options.source) {
        cmd << "--source" << options.source
    }
    
    return cmd
}

/**
 * Execute Haven upload command.
 * 
 * @param cmd List of command arguments
 * @return Map with exitCode, stdout, stderr
 */
def executeHavenUpload(cmd) {
    log.fine "Executing: ${cmd.join(' ')}"
    
    def process = cmd.execute()
    process.waitFor()
    
    return [
        exitCode: process.exitValue(),
        stdout: process.text,
        stderr: process.err.text
    ]
}

/**
 * Upload file to Haven/Filecoin with retry logic.
 * 
 * @param file The file to upload
 * @param options Map of upload options (see buildHavenCommand)
 * @param maxRetries Maximum number of retry attempts (default: 3)
 * @return Map with success, result/error, category
 */
def uploadToHaven(file, options = [:], maxRetries = 3) {
    def cmd = buildHavenCommand(file, options)
    
    for (attempt in 0..maxRetries) {
        def result = executeHavenUpload(cmd)
        
        if (result.exitCode == 0) {
            // Extract CID from stdout if possible
            def cidMatch = result.stdout =~ /CID:\s*([a-zA-Z0-9]+)/
            if (cidMatch) {
                result.cid = cidMatch[0][1]
            }
            return [success: true, result: result]
        }
        
        def category = categorizeHavenError(result.stderr)
        
        if (category == 'permanent' || attempt == maxRetries) {
            return [success: false, error: result.stderr, category: category, exitCode: result.exitCode]
        }
        
        // Exponential backoff
        def delay = Math.pow(2, attempt) * 1000
        log.fine "Retrying Haven upload in ${delay}ms (attempt ${attempt + 1}/$maxRetries)"
        sleep(delay as long)
    }
}

/**
 * Categorize Haven errors for retry decisions.
 * 
 * @param error Error message or exception
 * @return String category: 'permanent', 'transient', or 'unknown'
 */
def categorizeHavenError(error) {
    if (!error) return 'unknown'
    def errorStr = error.toString().toLowerCase()
    
    // Permanent errors - don't retry
    if (errorStr =~ /insufficient (balance|funds)|unauthorized|forbidden|401|403|404|invalid/) {
        return 'permanent'
    }
    
    // Transient errors - retry
    if (errorStr =~ /timeout|connection|network|rate limit|503|502|504|temporary|unavailable/) {
        return 'transient'
    }
    
    return 'unknown'
}

/**
 * Extract metadata from FileBot's xattr metadata for Haven upload.
 * 
 * @param file The media file with FileBot metadata
 * @return Map with title, creator, source
 */
def extractHavenMetadata(file) {
    def metadata = [:]
    
    // Access FileBot's internal metadata
    def fileMetadata = file.metadata
    
    if (fileMetadata) {
        // TV Series metadata
        if (fileMetadata.seriesInfo) {
            def series = fileMetadata.seriesInfo
            def episode = fileMetadata.episode
            
            def seasonStr = episode?.season?.toString()?.padLeft(2, '0') ?: '00'
            def episodeStr = episode?.number?.toString()?.padLeft(2, '0') ?: '00'
            def episodeTitle = episode?.title ?: 'Unknown'
            
            metadata.title = "${series.name} - S${seasonStr}E${episodeStr} - ${episodeTitle}"
            metadata.creator = series.network ?: series.studio ?: "Unknown"
            metadata.source = series.id ? "tvdb:${series.id}" : null
            metadata.type = 'series'
            
            log.fine "Extracted series metadata: ${series.name} S${seasonStr}E${episodeStr}"
        }
        // Movie metadata
        else if (fileMetadata.movieInfo) {
            def movie = fileMetadata.movieInfo
            def year = movie.released?.year ?: movie.year
            
            metadata.title = year ? "${movie.name} (${year})" : movie.name
            metadata.creator = movie.productionCompanies?.join(', ') ?: movie.director?.join(', ') ?: "Unknown"
            metadata.source = movie.imdbId ? "imdb:tt${movie.imdbId.toString().padLeft(7, '0')}" : (movie.id ? "tmdb:${movie.id}" : null)
            metadata.type = 'movie'
            
            log.fine "Extracted movie metadata: ${movie.name} (${year})"
        }
    }
    
    // Fallback to filename parsing
    if (!metadata.title) {
        metadata.title = file.nameWithoutExtension
        metadata.creator = "Unknown"
        metadata.type = 'unknown'
        log.fine "Using filename as title: ${metadata.title}"
    }
    
    return metadata
}

/**
 * Extract metadata from rename log (for AMC integration).
 * 
 * @param file The destination file
 * @param renameLog Map of original -> destination files
 * @return Map with title, creator, source, originalFile
 */
def extractHavenMetadataFromLog(file, renameLog) {
    def metadata = extractHavenMetadata(file)
    
    // Get the original file name from rename log
    def originalFile = renameLog.find { k, v -> v == file }?.key
    if (originalFile) {
        metadata.originalFile = originalFile.name
    }
    
    return metadata
}

/**
 * Create a queue entry for background processing.
 * 
 * @param file The file to queue
 * @param metadata Map of metadata
 * @param options Map of upload options
 * @return File The queue entry file
 */
def queueForHavenUpload(file, metadata, options) {
    def queueDir = new File("${System.getProperty('user.home')}/.filebot/haven-queue")
    queueDir.mkdirs()
    
    def timestamp = System.currentTimeMillis()
    def queueFile = new File(queueDir, "${timestamp}_${file.name}.json")
    
    def queueEntry = [
        file: file.path,
        metadata: metadata,
        options: [
            encrypt: options.encrypt,
            vlm: options.vlm,
            arkiv: options.arkiv,
            dataset: options.dataset,
            config: options.config?.toString()
        ],
        queuedAt: new Date().toString(),
        attempts: 0
    ]
    
    queueFile.write(groovy.json.JsonOutput.toJson(queueEntry))
    log.fine "Queued for Haven upload: $file"
    return queueFile
}

/**
 * Process pending uploads in the queue.
 * 
 * @param options Map of default options
 * @return Map with processed, succeeded, failed counts
 */
def processHavenQueue(options = [:]) {
    def queueDir = new File("${System.getProperty('user.home')}/.filebot/haven-queue")
    if (!queueDir.exists()) {
        return [processed: 0, succeeded: 0, failed: 0]
    }
    
    def stats = [processed: 0, succeeded: 0, failed: 0]
    def queueFiles = queueDir.listFiles()?.findAll { it.name.endsWith('.json') } ?: []
    
    queueFiles.each { queueFile ->
        try {
            def entry = new groovy.json.JsonSlurper().parse(queueFile)
            def file = new File(entry.file)
            
            if (!file.exists()) {
                log.warning "Queued file no longer exists: $file"
                queueFile.delete()
                return
            }
            
            // Merge stored options with defaults
            def uploadOptions = entry.options + options
            def result = uploadToHaven(file, uploadOptions)
            
            stats.processed++
            if (result.success) {
                stats.succeeded++
                queueFile.delete()
                log.info "Haven queue upload successful: $file.name"
            } else {
                stats.failed++
                // Update attempts counter
                entry.attempts = (entry.attempts ?: 0) + 1
                if (entry.attempts >= 3) {
                    log.warning "Max retries reached for $file.name, removing from queue"
                    queueFile.delete()
                } else {
                    entry.lastError = result.error
                    queueFile.write(groovy.json.JsonOutput.toJson(entry))
                }
            }
        } catch (e) {
            log.warning "Error processing queue file $queueFile: $e"
        }
    }
    
    return stats
}

/**
 * Get queue status summary.
 * 
 * @return Map with pending, total files
 */
def getHavenQueueStatus() {
    def queueDir = new File("${System.getProperty('user.home')}/.filebot/haven-queue")
    if (!queueDir.exists()) {
        return [pending: 0, total: 0]
    }
    
    def files = queueDir.listFiles()?.findAll { it.name.endsWith('.json') } ?: []
    return [pending: files.size(), total: files.size()]
}

/**
 * Format Haven results for notifications.
 * 
 * @param results List of Haven result maps
 * @return String formatted message
 */
def formatHavenResults(results) {
    if (!results || results.isEmpty()) {
        return "No Haven uploads processed"
    }
    
    def successCount = results.count { it.status == 'success' }
    def failedCount = results.count { it.status == 'failed' }
    
    def lines = ["Haven Archive: $successCount uploaded"]
    if (failedCount > 0) {
        lines[0] += ", $failedCount failed"
    }
    
    results.each { r ->
        def symbol = r.status == 'success' ? '✓' : '✗'
        def cid = r.cid ? " (${r.cid.take(12)}...)" : ''
        lines << "$symbol ${r.file ?: 'unknown'}$cid"
    }
    
    return lines.join('\n')
}

/**
 * Haven upload options container class.
 * Provides a type-safe way to pass options.
 */
class HavenOptions {
    Boolean encrypt = false
    Boolean vlm = true
    Boolean arkiv = true
    Integer dataset = null
    File config = null
    Boolean failOnError = false
    Boolean queue = false
    Integer maxRetries = 3
    
    def toMap() {
        return [
            encrypt: encrypt,
            vlm: vlm,
            arkiv: arkiv,
            dataset: dataset,
            config: config,
            maxRetries: maxRetries
        ]
    }
}

/**
 * Create HavenOptions from script parameters.
 * 
 * @param params Map of script parameters
 * @return HavenOptions
 */
def createHavenOptions(params) {
    def options = new HavenOptions()
    
    options.encrypt = tryQuietly { params.havenEncrypt?.toBoolean() } ?: false
    options.vlm = tryQuietly { params.havenVLM?.toBoolean() } ?: true
    options.arkiv = tryQuietly { params.havenArkiv?.toBoolean() } ?: true
    options.dataset = tryQuietly { params.havenDataset as Integer }
    options.config = tryQuietly { params.havenConfig as File }
    options.failOnError = tryQuietly { params.havenFailOnError?.toBoolean() } ?: false
    options.queue = tryQuietly { params.havenQueue?.toBoolean() } ?: false
    options.maxRetries = tryQuietly { params.havenMaxRetries as Integer } ?: 3
    
    return options
}
