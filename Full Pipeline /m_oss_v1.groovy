def palamida_url = "http://172.18.228.177:8888"
def flexnet_auth_token = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJTd2FwbmlsIiwidXNlcklkIjo0LCJpYXQiOjE3NzEyMzM1NTN9.zrim2FCPiwCdDKurVhPRk6R07GzJu93msva6lhsmeVT5Eu1TLuB7YzJf9Uo1NaxXtwRxFJn5gGYZ_2_1bWG4Jg"

def taskId
def extractedValue = null



/* =========================
   SAFE PREVIOUS BUILD LOG READ
   ========================= */
@NonCPS
def getExtractedValueFromPreviousBuild() {
    def prev = currentBuild.rawBuild?.previousBuild
    if (!prev) {
        return null
    }

    def logText = prev.getLogFile().text
    def key = "Initializing execution context for"
    def line = logText.readLines().find { it.contains(key) }

    return line ? line.split(key)[1].trim() : null
}

/* =========================
   EXECUTION
   ========================= */

extractedValue = getExtractedValueFromPreviousBuild()

if (extractedValue) {
    echo "Extracted Value: ${extractedValue}"
} else {
    echo "No previous build or matching line found."
}


/* =========================
   CLEAN REMOTE DIRECTORY
   ========================= */

if (extractedValue) {
    sshPublisher(
        publishers: [
            sshPublisherDesc(
                configName: 'Flexnet',
                transfers: [
                    sshTransfer(
                        execCommand: "cd /tmp && sudo rm -rf ${extractedValue}",
                        execTimeout: '120000'
                    )
                ],
                verbose: true
            )
        ]
    )
}


/* =========================
   START SCAN
   ========================= */

sh 'echo Initiating OSS Scan'

sshPublisher(
    publishers: [
        sshPublisherDesc(
            configName: 'Flexnet',
            transfers: [
                sshTransfer(
                    execCommand: """
                        cd code-insight-agent-sdk-generic-plugin/generic-plugin-binary &&
                        sudo java -Dflx.agent.reset=true -Dflx.agent.logLevel=error \
                        -jar codeinsight-generic-3.1.20.jar \
                        -server "${palamida_url}/codeinsight" \
                        -token "Bearer ${flexnet_auth_token}" \
                        -proj "${params.PROJECT_PALAMIDA}" \
                        -root "/nfs_shared_files" \
                        -scandirs "/nfs_shared_files/${params.PROJECT}/${params.VERSION}" \
                        -alias "Scanner-core_${BUILD_NUM}" \
                        -host "172.18.228.174"
                    """,
                    execTimeout: 86400000
                )
            ],
            verbose: true
        )
    ]
)

echo ".........Initiating OSS Scan Completed.........."    


/* =========================
   GENERATE REPORT
   ========================= */

sh """
curl -X POST \
"${palamida_url}/codeinsight/api/projects/${REMOTE_DIR}/reports/1/generate" \
-H "accept: application/json" \
-H "Authorization: Bearer ${flexnet_auth_token}" \
-H "content-type: application/json" \
-d "{}" > response.json
"""

taskId = sh(
    script: "jq -r '.data.taskId' response.json",
    returnStdout: true
).trim()

echo "Task Id is: ${taskId}"


/* =========================
   DOWNLOAD REPORT WITH RETRY
   ========================= */

def downloadReport = ''
def filename = ''
def maxRetries = 30
def retries = 0

while (retries < maxRetries) {

    def headers = sh(
        script: """
            curl -sI \
            "${palamida_url}/codeinsight/api/projects/${REMOTE_DIR}/reports/1/download?taskId=${taskId}" \
            -H "Authorization: Bearer ${flexnet_auth_token}"
        """,
        returnStdout: true
    ).trim()

    headers.split("\n").each { line ->
        if (line.startsWith('Content-Disposition:')) {
            filename = line.split(';').find { it.trim().startsWith('filename=') }
                ?.split('=')[1]
                ?.replaceAll('"', '')
        }
    }

    if (!filename) {
        echo "Filename not yet available (${retries}/${maxRetries})"
        retries++
        sleep(60)
        continue
    }

    downloadReport = sh(
        script: """
            curl -L \
            -o ${params.PROJECT}.zip \
            "${palamida_url}/codeinsight/api/projects/${REMOTE_DIR}/reports/1/download?taskId=${taskId}" \
            -H "Authorization: Bearer ${flexnet_auth_token}" \
            -w "%{http_code}" -s -o /dev/null
        """,
        returnStdout: true
    ).trim()

    if (downloadReport == "200") {
        break
    }

    retries++
    sleep(30)
}

if (retries == maxRetries) {
    error "Report generation still in progress after ${maxRetries} retries"
}

echo "Downloaded Report: ${params.PROJECT}.zip"
archiveArtifacts artifacts: "${params.PROJECT}.zip", allowEmptyArchive: true
sh "pwd"


/* =========================
   OPTIONAL TASK STATUS CHECK
   ========================= */
def waitForTaskToComplete(String taskId, int retries, int timeoutMinutes) {
    for (int i = 0; i < retries; i++) {
        def status = sh(
            script: """
                curl -s \
                "${palamida_url}/codeinsight/api/jobs/${taskId}" \
                -H "Authorization: Bearer ${flexnet_auth_token}" |
                jq -r .status
            """,
            returnStdout: true
        ).trim()

        if (status == "Completed") {
            return 0
        }

        echo "Scan not completed yet. Waiting ${timeoutMinutes} minutes"
        sleep(time: timeoutMinutes, unit: 'MINUTES')
    }
    return 1
}


