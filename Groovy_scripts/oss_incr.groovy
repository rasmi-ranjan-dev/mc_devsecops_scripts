def palamida_url = "http://172.18.228.177:8888"
def taskId = ""
def reportFile = "${params.PROJECT}.zip"
def flexnet_auth_token = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJwYWxhbWlkYSIsInVzZXJJZCI6MywiaWF0IjoxNzgxMTg0MzExfQ.I1tiuxFOVN9jwcJpNhsXKAC4QvsHjqvupdycTGoXzGWnMsMgwArf4r8x8YmM8PpVzY0Fela5KHkM3jGD-TemrQ"

/* =====================================================
   STEP 1: PRINT INPUT PARAMETERS
   ===================================================== */
echo "=========================================="
echo "OSS Scan Pipeline Started"
echo "Project              : ${params.PROJECT}"
echo "Version              : ${params.VERSION}"
echo "Remote Dir           : ${params.REMOTE_DIR}"
echo "BUILD_TYPE           : ${params.BUILD_TYPE}"
echo "=========================================="

/* =====================================================
   STEP 2: VALIDATE REQUIRED PARAMETERS
   ===================================================== */
if (!params.PROJECT?.trim()) error "PROJECT parameter is empty."
if (!params.VERSION?.trim()) error "VERSION parameter is empty."
if (!params.REMOTE_DIR?.toString()?.trim()) error "REMOTE_DIR parameter is empty."
if (!params.BUILD_TYPE?.trim()) error "BUILD_TYPE parameter missing."

/* =====================================================
   STEP 3: DECIDE FULL OR INCREMENTAL
   ===================================================== */
def buildType = params.BUILD_TYPE.trim().toUpperCase()
def scanType = ""
def resetFlag = ""

if (buildType == "FULL") {
    scanType = "FULL"
    resetFlag = "true"
} else if (buildType == "INCREMENTAL") {
    scanType = "INCREMENTAL"
    resetFlag = "false"
} else {
    error "Invalid BUILD_TYPE"
}

def scanDir = "/shared_files/${params.PROJECT}/${params.VERSION}"

echo "Scan Type: ${scanType}"



/* =====================================================
   STEP 5: GENERATE INCREMENTAL CODEBASE
   ===================================================== */

def INCR_CODEBASE_LOCAL  = "/shared_files/${params.PROJECT}/incremental/INCREMENTAL_CODEBASE"
def INCR_CODEBASE_REMOTE = "/nfs_shared_files/${params.PROJECT}/incremental/INCREMENTAL_CODEBASE"

if (buildType == "INCREMENTAL") {

    def FULL_CODEBASE = "/shared_files/${params.PROJECT}/test_7.0"
    def GIT_DIFF_PATH = "/shared_files/${params.PROJECT}/incremental/changed_files.txt"

    echo "FULL_CODEBASE : ${FULL_CODEBASE}"
    echo "INCR_CODEBASE : ${INCR_CODEBASE_LOCAL}"
    echo "GIT_DIFF_PATH : ${GIT_DIFF_PATH}"

    // =====================================================
    // Run incremental copy logic on remote Palamida server
    // =====================================================
    sh """


    sudo mkdir -p "${INCR_CODEBASE_LOCAL}"

    echo "Reading paths from: ${GIT_DIFF_PATH}"

    if [ ! -f "${GIT_DIFF_PATH}" ]; then
        echo "ERROR: Changed files file not found: ${GIT_DIFF_PATH}"
        echo "Available files:"
        ls -lah "/shared_files/${params.PROJECT}/incremental" || true
        exit 1
    fi

    if [ ! -d "${FULL_CODEBASE}" ]; then
        echo "ERROR: Full codebase directory not found: ${FULL_CODEBASE}"
        exit 1
    fi

    while IFS= read -r relative_path; do
        # Remove leading/trailing whitespaces
        trimmed_path=\$(echo "\$relative_path" | xargs)

        # Skip empty lines
        [ -z "\$trimmed_path" ] && continue

        echo "Processing: \$trimmed_path"

        # Find full path inside FULL_CODEBASE
        matched_files=\$(find "${FULL_CODEBASE}" -path "*\$trimmed_path*" -type f)

        for file in \$matched_files; do
            echo "Copying file: \$file"

            # Construct destination directory in INCR_CODEBASE
            rel_path=\$(realpath --relative-to="${FULL_CODEBASE}" "\$file")
            dest_dir="${INCR_CODEBASE_LOCAL}/\$(dirname "\$rel_path")"

            sudo mkdir -p "\$dest_dir"
            sudo cp "\$file" "\$dest_dir/"
        done
    done < "${GIT_DIFF_PATH}"

    echo "Incremental codebase creation completed."

            """
}

/* =====================================================
   STEP 6: OSS SCAN
   ===================================================== */
echo "Starting OSS Scan using ${INCR_CODEBASE_LOCAL}"

sshPublisher(
    publishers: [
        sshPublisherDesc(
            configName: 'Flexnet',
            transfers: [
                sshTransfer(
                    execCommand: """
                        set -e

                        echo "Running ${scanType} Scan"
                        echo "Scan Directory: ${INCR_CODEBASE_REMOTE}"

                        cd code-insight-agent-sdk-generic-plugin/generic-plugin-binary

                        sudo /usr/lib/jvm/java-17-openjdk-17.0.19.0.10-1.el8.x86_64/bin/java \\
                        -Dflx.agent.reset=${resetFlag} \\
                        -Dflx.agent.logLevel=error \\
                        -jar codeinsight-generic-3.1.20.jar \\
                        -server "${palamida_url}/codeinsight" \\
                        -token "Bearer ${flexnet_auth_token}" \\
                        -proj "${params.PROJECT}" \\
                        -root "/nfs_shared_files" \\
                        -scandirs "${INCR_CODEBASE_REMOTE}" \\
                        -alias "Scanner-core-${env.BUILD_NUM}" \\
                        -host "172.18.228.174"

                        echo "Scan completed"
                    """,
                    execTimeout: 86400000
                )
            ],
            verbose: true
        )
    ]
)

echo "OSS Scan Completed"

/* =====================================================
   STEP 7: RESOLVE PROJECT ID
   ===================================================== */
 
def projectId = sh(
    script: """
        curl -s -X GET \
        "${palamida_url}/codeinsight/api/v1/project/id?projectName=${params.PROJECT}" \
        -H "Authorization: Bearer ${flexnet_auth_token}" | grep -o '[0-9]\\+'
    """,
    returnStdout: true
).trim()

echo "Resolved CodeInsight Project ID: ${projectId}"

 
if (!projectId?.trim() || projectId == "null") {
    error "Failed to resolve project ID for project ${params.PROJECT}"
}

/* =====================================================
   STEP 8: GENERATE REPORT
   ===================================================== */
sh """
    curl -s -X POST \
    "${palamida_url}/codeinsight/api/projects/${projectId}/reports/1/generate" \
    -H "Authorization: Bearer ${flexnet_auth_token}" \
    -H "Content-Type: application/json" \
    -d '{}' > response.json
"""

sh "cat response.json"

taskId = sh(
    script: "jq -r '.data.taskId // empty' response.json",
    returnStdout: true
).trim()

if (!taskId) error "Failed to get taskId"

echo "Task ID: ${taskId}"

/* =====================================================
   STEP 9: DOWNLOAD REPORT
   ===================================================== */
def retries = 0
while (retries < 30) {

    def code = sh(
        script: """
            curl -L -o "${reportFile}" \\
            "${palamida_url}/codeinsight/api/projects/${projectId}/reports/1/download?taskId=${taskId}" \\
            -H "Authorization: Bearer ${flexnet_auth_token}" \\
            -w "%{http_code}" -s
        """,
        returnStdout: true
    ).trim()

    if (code == "200") break

    sleep(30)
    retries++
}

if (retries == 30) error "Report download timeout"

/* =====================================================
   STEP 10: ARCHIVE
   ===================================================== */
archiveArtifacts artifacts: "${reportFile}"

echo "===== PIPELINE COMPLETED SUCCESSFULLY ====="
 

